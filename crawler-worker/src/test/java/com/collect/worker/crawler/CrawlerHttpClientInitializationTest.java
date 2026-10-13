package com.collect.worker.crawler;

import com.collect.worker.mapper.FileMetadataMapper;
import com.collect.worker.mapper.SpiderTaskLogMapper;
import com.collect.worker.mapper.SpiderTaskMapper;
import com.collect.worker.minio.MinioHelper;
import com.collect.worker.redis.UrlQueueService;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CrawlerHttpClientInitializationTest {

    @Test
    void initializeHttpClients_shouldUseInjectedSettings() {
        CrawlerEngine engine = new CrawlerEngine(
                mock(SpiderTaskMapper.class),
                mock(SpiderTaskLogMapper.class),
                mock(ElasticsearchOperations.class),
                mock(UrlQueueService.class),
                mock(MinioHelper.class),
                mock(FileMetadataMapper.class),
                mock(KafkaTemplate.class));
        ReflectionTestUtils.setField(engine, "httpConnectTimeoutSeconds", 15);
        ReflectionTestUtils.setField(engine, "httpReadTimeoutSeconds", 30);
        ReflectionTestUtils.setField(engine, "httpMaxIdleConnections", 512);
        ReflectionTestUtils.setField(engine, "httpKeepAliveMinutes", 2);
        ReflectionTestUtils.setField(engine, "httpMaxConcurrentRequests", 2000);
        ReflectionTestUtils.setField(engine, "httpMaxConcurrentRequestsPerClient", 200);
        ReflectionTestUtils.setField(engine, "httpRetryOnIoError", 2);
        ReflectionTestUtils.setField(engine, "httpDnsCacheTtlSeconds", 0);
        ReflectionTestUtils.setField(engine, "httpProtocol", "http1");

        engine.initializeHttpClients();

        OkHttpClient client = (OkHttpClient) ReflectionTestUtils.getField(engine, "verifiedHttpClient");
        assertEquals(List.of(Protocol.HTTP_1_1), client.protocols());
        assertEquals(15_000, client.connectTimeoutMillis());
        assertEquals(30_000, client.readTimeoutMillis());
        assertEquals(2000, client.dispatcher().getMaxRequests());
        assertEquals(200, client.dispatcher().getMaxRequestsPerHost());
    }
}
