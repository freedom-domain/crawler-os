package com.collect.worker.crawler;

import com.collect.common.mq.TaskMessage;
import com.collect.worker.es.SpiderContentDoc;
import com.collect.worker.entity.SpiderTask;
import com.collect.worker.entity.SpiderTaskLog;
import com.collect.worker.mapper.SpiderTaskLogMapper;
import com.collect.worker.mapper.SpiderTaskMapper;
import com.collect.worker.minio.MinioHelper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CrawlerEngine {

    private final SpiderTaskMapper taskMapper;
    private final SpiderTaskLogMapper logMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final MinioHelper minioHelper;

    @Value("${app.es.content-index:spider_content}")
    private String contentIndex;

    public CrawlerEngine(SpiderTaskMapper taskMapper, SpiderTaskLogMapper logMapper,
                         ElasticsearchOperations elasticsearchOperations, MinioHelper minioHelper) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.elasticsearchOperations = elasticsearchOperations;
        this.minioHelper = minioHelper;
    }

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private static final String MINIO_BUCKET = "crawler";
    private static final String HTML_PREFIX = "html/";

    public void execute(TaskMessage msg) {
        SpiderTask task = taskMapper.selectByTaskId(msg.getTaskId());
        if (task == null) {
            log.warn("任务不存在: {}", msg.getTaskId());
            return;
        }

        int success = 0;
        int fail = 0;
        Set<String> visited = ConcurrentHashMap.newKeySet();
        int depth = 0;
        int maxDepth = msg.getMaxDepth() != null ? msg.getMaxDepth() : 2;

        for (String startUrl : msg.getStartUrls()) {
            if (visited.size() >= 100) break;
            try {
                crawlStartUrl(startUrl, msg, task, visited, depth, maxDepth);
                success++;
            } catch (Exception e) {
                fail++;
                log.error("抓取失败: url={}", startUrl, e);
                writeLog(task.getId(), msg.getSpiderId(), startUrl, 0, "ERROR",
                        e.getMessage(), 0);
            }
        }

        taskMapper.incrSuccess(task.getId(), success);
        taskMapper.incrFail(task.getId(), fail);
        task.setStatus("SUCCESS");
        task.setEndTime(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("任务完成: taskId={}, success={}, fail={}", msg.getTaskId(), success, fail);
    }

    private void crawlStartUrl(String url, TaskMessage msg, SpiderTask task,
                               Set<String> visited, int depth, int maxDepth) throws Exception {
        if (!visited.add(url)) {
            return;
        }
        if (visited.size() > 100) {
            return;
        }

        long start = System.currentTimeMillis();
        Document doc = fetch(url, msg.getTimeout());
        long cost = System.currentTimeMillis() - start;

        ContentParser parsed = ContentParser.parse(doc.outerHtml(), url);

        SpiderContentDoc docObj = new SpiderContentDoc();
        docObj.setId(UUID.randomUUID().toString().replace("-", ""));
        docObj.setTitle(parsed.getTitle());
        docObj.setContent(parsed.getContent());
        docObj.setUrl(url);
        docObj.setAuthor(parsed.getAuthor());
        docObj.setSpiderId(msg.getSpiderId());
        docObj.setSpiderName(msg.getSpiderName());
        docObj.setSourceType(msg.getType());
        docObj.setCrawlTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));

        String objectName = HTML_PREFIX + msg.getSpiderId() + "/" +
                System.currentTimeMillis() + "_" + Integer.toHexString(url.hashCode()) + ".html";
        minioHelper.putHtml(MINIO_BUCKET, objectName, doc.outerHtml());
        docObj.setFileId(objectName);

        elasticsearchOperations.save(docObj, IndexCoordinates.of(contentIndex));
        writeLog(task.getId(), msg.getSpiderId(), url, 1, "INFO",
                "抓取成功: " + parsed.getTitle(), (int) cost);

        if (depth < maxDepth) {
            List<String> next = ContentParser.extractNextUrls(doc, url, maxDepth - depth);
            for (String nextUrl : next) {
                if (visited.size() >= 100) break;
                if (visited.add(nextUrl)) {
                    try {
                        crawlStartUrl(nextUrl, msg, task, visited, depth + 1, maxDepth);
                    } catch (Exception e) {
                        log.warn("子链接抓取失败: {}", nextUrl, e);
                        writeLog(task.getId(), msg.getSpiderId(), nextUrl, 0, "ERROR",
                                e.getMessage(), 0);
                    }
                }
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
