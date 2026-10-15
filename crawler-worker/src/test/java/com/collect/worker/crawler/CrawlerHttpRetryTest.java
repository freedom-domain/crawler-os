package com.collect.worker.crawler;

import com.collect.worker.mapper.FileMetadataMapper;
import com.collect.worker.mapper.SpiderTaskLogMapper;
import com.collect.worker.mapper.SpiderTaskMapper;
import com.collect.worker.minio.MinioHelper;
import com.collect.worker.redis.UrlQueueService;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlerHttpRetryTest {

    @Test
    void retryInterceptor_shouldRetry404AndReturnSuccessfulResponse() throws Exception {
        CrawlerEngine engine = createEngine();
        ReflectionTestUtils.setField(engine, "httpRetryOnIoError", 2);
        Interceptor interceptor = (Interceptor) ReflectionTestUtils.invokeMethod(
                engine, "retryOnIoErrorInterceptor");
        Request request = new Request.Builder().url("https://example.com/page").build();
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        AtomicInteger attempts = new AtomicInteger();
        when(chain.request()).thenReturn(request);
        when(chain.proceed(any(Request.class))).thenAnswer(invocation ->
                response(request, attempts.incrementAndGet() == 1 ? 404 : 200));

        Response result = interceptor.intercept(chain);

        try {
            assertEquals(200, result.code());
            assertEquals(2, attempts.get());
        } finally {
            result.close();
        }
    }

    @Test
    void retryInterceptor_shouldReturn404AfterConfiguredAttempts() throws Exception {
        CrawlerEngine engine = createEngine();
        ReflectionTestUtils.setField(engine, "httpRetryOnIoError", 2);
        Interceptor interceptor = (Interceptor) ReflectionTestUtils.invokeMethod(
                engine, "retryOnIoErrorInterceptor");
        Request request = new Request.Builder().url("https://example.com/missing").build();
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        AtomicInteger attempts = new AtomicInteger();
        when(chain.request()).thenReturn(request);
        when(chain.proceed(any(Request.class))).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            return response(request, 404);
        });

        Response result = interceptor.intercept(chain);

        try {
            assertEquals(404, result.code());
            assertEquals(3, attempts.get());
        } finally {
            result.close();
        }
    }

    private static CrawlerEngine createEngine() {
        return new CrawlerEngine(
                mock(SpiderTaskMapper.class),
                mock(SpiderTaskLogMapper.class),
                mock(ElasticsearchOperations.class),
                mock(UrlQueueService.class),
                mock(MinioHelper.class),
                mock(FileMetadataMapper.class),
                mock(KafkaTemplate.class));
    }

    private static Response response(Request request, int code) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 404 ? "Not Found" : "OK")
                .body(ResponseBody.create(new byte[0], null))
                .build();
    }
}
