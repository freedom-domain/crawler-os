package com.collect.worker.crawler;

import com.collect.common.mq.TaskMessage;
import com.collect.worker.es.SpiderContentDoc;
import com.collect.worker.entity.SpiderTask;
import com.collect.worker.entity.SpiderTaskLog;
import com.collect.worker.mapper.SpiderTaskLogMapper;
import com.collect.worker.mapper.SpiderTaskMapper;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class CrawlerEngine {

    private final SpiderTaskMapper taskMapper;
    private final SpiderTaskLogMapper logMapper;
    private final ElasticsearchOperations elasticsearchOperations;

    @Value("${app.es.content-index:spider_content}")
    private String contentIndex;

    public CrawlerEngine(SpiderTaskMapper taskMapper, SpiderTaskLogMapper logMapper,
                         ElasticsearchOperations elasticsearchOperations) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.elasticsearchOperations = elasticsearchOperations;
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

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);
        Set<String> visited = ConcurrentHashMap.newKeySet();
        int maxDepth = msg.getMaxDepth() != null ? msg.getMaxDepth() : 2;
        int concurrency = 8;

        var semaphore = new java.util.concurrent.Semaphore(concurrency);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();

            for (String startUrl : msg.getStartUrls()) {
                if (visited.size() >= 100) break;
                futures.add(executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            crawlStartUrl(startUrl, msg, task, visited, 0, maxDepth, success, fail, semaphore, executor);
                        } finally {
                            semaphore.release();
                        }
                    } catch (Exception e) {
                        fail.incrementAndGet();
                        log.error("抓取失败: url={}", startUrl, e);
                        writeLog(task.getId(), msg.getSpiderId(), startUrl, 0, "ERROR",
                                e.getMessage(), 0);
                    }
                    return null;
                }));
            }

            for (var f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }
        }

        taskMapper.incrSuccess(task.getId(), success.get());
        taskMapper.incrFail(task.getId(), fail.get());
        task.setStatus("SUCCESS");
        task.setEndTime(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("任务完成: taskId={}, success={}, fail={}", msg.getTaskId(), success.get(), fail.get());
    }

    private void crawlStartUrl(String url, TaskMessage msg, SpiderTask task,
                               Set<String> visited, int depth, int maxDepth,
                               AtomicInteger success, AtomicInteger fail,
                               java.util.concurrent.Semaphore semaphore,
                               java.util.concurrent.ExecutorService executor) throws Exception {
        if (!visited.add(url)) {
            return;
        }
        if (visited.size() > 100) {
            return;
        }

        long start = System.currentTimeMillis();
        Document doc;
        try {
            doc = fetch(url, msg.getTimeout());
        } catch (Exception e) {
            fail.incrementAndGet();
            log.warn("子链接抓取失败: {}", url, e);
            writeLog(task.getId(), msg.getSpiderId(), url, 0, "ERROR", e.getMessage(), 0);
            return;
        }
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
            List<String> toCrawl = new java.util.ArrayList<>();
            for (String nextUrl : next) {
                if (visited.size() >= 100) break;
                if (visited.add(nextUrl)) {
                    toCrawl.add(nextUrl);
                }
            }
            log.info("depth={}, url={}, extracted={}, toCrawl={}", depth, url, next.size(), toCrawl.size());
            for (String nextUrl : toCrawl) {
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            crawlStartUrl(nextUrl, msg, task, visited, depth + 1, maxDepth, success, fail, semaphore, executor);
                        } finally {
                            semaphore.release();
                        }
                    } catch (Exception e) {
                        fail.incrementAndGet();
                        log.warn("子链接抓取失败: {}", nextUrl, e);
                        writeLog(task.getId(), msg.getSpiderId(), nextUrl, 0, "ERROR",
                                e.getMessage(), 0);
                    }
                });
            }
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
