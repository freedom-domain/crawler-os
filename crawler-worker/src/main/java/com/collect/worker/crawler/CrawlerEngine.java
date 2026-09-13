package com.collect.worker.crawler;

import com.collect.common.mq.TaskMessage;
import com.collect.worker.es.SpiderContentDoc;
import com.collect.worker.entity.SpiderTask;
import com.collect.worker.entity.SpiderTaskLog;
import com.collect.worker.mapper.SpiderTaskLogMapper;
import com.collect.worker.mapper.SpiderTaskMapper;
import com.collect.worker.redis.UrlQueueService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class CrawlerEngine {

    private final SpiderTaskMapper taskMapper;
    private final SpiderTaskLogMapper logMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final UrlQueueService urlQueue;

    @Value("${app.es.content-index:spider_content}")
    private String contentIndex;

    public CrawlerEngine(SpiderTaskMapper taskMapper, SpiderTaskLogMapper logMapper,
                         ElasticsearchOperations elasticsearchOperations, UrlQueueService urlQueue) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.elasticsearchOperations = elasticsearchOperations;
        this.urlQueue = urlQueue;
    }

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    public void execute(TaskMessage msg) {
        SpiderTask task = taskMapper.selectByTaskId(msg.getTaskId());
        if (task == null) {
            log.warn("任务不存在: {}", msg.getTaskId());
            return;
        }

        Long taskId = msg.getTaskId();
        int maxDepth = msg.getMaxDepth() != null ? msg.getMaxDepth() : 2;
        int concurrency = 8;
        var semaphore = new java.util.concurrent.Semaphore(concurrency);
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);

        try {
            for (String startUrl : msg.getStartUrls()) {
                if (urlQueue.isVisited(taskId, startUrl)) continue;
                urlQueue.markVisited(taskId, startUrl);
                urlQueue.push(taskId, startUrl + "\t0");
            }

            while (urlQueue.size(taskId) > 0) {
                List<Runnable> batch = new java.util.ArrayList<>();
                for (int i = 0; i < concurrency; i++) {
                    String item = urlQueue.pop(taskId);
                    if (item == null) break;
                    String[] parts = item.split("\t", 2);
                    String url = parts[0];
                    int depth = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    batch.add(() -> crawlUrl(url, depth, maxDepth, msg, task, taskId, success, fail, semaphore, executor));
                }
                if (batch.isEmpty()) break;
                var futures = batch.stream().map(executor::submit).toList();
                for (var f : futures) {
                    try { f.get(); } catch (Exception ignored) {}
                }
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            urlQueue.clear(taskId);
        }

        taskMapper.incrSuccess(task.getId(), success.get());
        taskMapper.incrFail(task.getId(), fail.get());
        task.setStatus("SUCCESS");
        task.setEndTime(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("任务完成: taskId={}, success={}, fail={}", taskId, success.get(), fail.get());
    }

    private void crawlUrl(String url, int depth, int maxDepth, TaskMessage msg, SpiderTask task,
                          Long taskId, AtomicInteger success, AtomicInteger fail,
                          java.util.concurrent.Semaphore semaphore,
                          java.util.concurrent.ExecutorService executor) {
        try {
            semaphore.acquire();
            try {
                long start = System.currentTimeMillis();
                Document doc = fetch(url, msg.getTimeout());
                long cost = System.currentTimeMillis() - start;

                ContentParser parsed = ContentParser.parse(doc.outerHtml(), url);

                SpiderContentDoc docObj = new SpiderContentDoc();
                docObj.setId(md5(url));
                docObj.setTitle(parsed.getTitle());
                docObj.setContent(parsed.getContent());
                docObj.setUrl(url);
                docObj.setAuthor(parsed.getAuthor());
                docObj.setSpiderId(msg.getSpiderId());
                docObj.setSpiderName(msg.getSpiderName());
                docObj.setSourceType(msg.getType());
                docObj.setCrawlTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
                docObj.setRawHtml(doc.outerHtml());

                elasticsearchOperations.save(docObj, IndexCoordinates.of(contentIndex));
                success.incrementAndGet();
                writeLog(task.getId(), msg.getSpiderId(), url, 1, "INFO",
                        "抓取成功: " + parsed.getTitle(), (int) cost);

                if (depth < maxDepth) {
                    List<String> next = ContentParser.extractNextUrls(doc, url, maxDepth - depth);
                    next.removeIf(u -> msg.getStartUrls().contains(u));
                    int enqueued = 0;
                    for (String nextUrl : next) {
                        if (urlQueue.isVisited(taskId, nextUrl)) continue;
                        urlQueue.markVisited(taskId, nextUrl);
                        urlQueue.push(taskId, nextUrl + "\t" + (depth + 1));
                        enqueued++;
                    }
                    log.info("depth={}, url={}, extracted={}, enqueued={}", depth, url, next.size(), enqueued);
                }
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            fail.incrementAndGet();
            log.warn("抓取失败: {}", url, e);
            writeLog(task.getId(), msg.getSpiderId(), url, 0, "ERROR", e.getMessage(), 0);
        }
    }

    private Document fetch(String url, Integer timeout) throws Exception {
        int timeoutMs = timeout != null ? timeout : 15000;
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code());
            }
            return Jsoup.parse(response.body().string(), url);
        }
    }

    private String md5(String input) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("MD5")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }

    private void writeLog(Long taskId, Long spiderId, String url, int status,
                          String level, String message, int costMs) {
        SpiderTaskLog logEntry = new SpiderTaskLog();
        logEntry.setTaskId(taskId);
        logEntry.setSpiderId(spiderId);
        logEntry.setUrl(url);
        logEntry.setStatus(status);
        logEntry.setLevel(level);
        logEntry.setMessage(message != null && message.length() > 500 ? message.substring(0, 500) : message);
        logEntry.setCostMs(costMs);
        logMapper.insert(logEntry);
    }
}
