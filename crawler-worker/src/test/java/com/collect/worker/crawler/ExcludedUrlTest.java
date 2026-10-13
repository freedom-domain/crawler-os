package com.collect.worker.crawler;

import com.collect.common.mq.TaskMessage;
import com.collect.worker.mapper.FileMetadataMapper;
import com.collect.worker.mapper.SpiderTaskLogMapper;
import com.collect.worker.mapper.SpiderTaskMapper;
import com.collect.worker.minio.MinioHelper;
import com.collect.worker.redis.UrlQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcludedUrlTest {

    private CrawlerEngine engine;
    private Method isExcludedUrl;
    private Method extractPathAndQuery;

    @BeforeEach
    void setUp() throws Exception {
        engine = new CrawlerEngine(
                Mockito.mock(SpiderTaskMapper.class),
                Mockito.mock(SpiderTaskLogMapper.class),
                Mockito.mock(ElasticsearchOperations.class),
                Mockito.mock(UrlQueueService.class),
                Mockito.mock(MinioHelper.class),
                Mockito.mock(FileMetadataMapper.class),
                Mockito.mock(KafkaTemplate.class)
        );
        isExcludedUrl = CrawlerEngine.class.getDeclaredMethod("isExcludedUrl", String.class, TaskMessage.class);
        isExcludedUrl.setAccessible(true);
        extractPathAndQuery = CrawlerEngine.class.getDeclaredMethod("extractPathAndQuery", String.class);
        extractPathAndQuery.setAccessible(true);
    }

    private TaskMessage msg(String excludedUrlsJson) {
        TaskMessage msg = new TaskMessage();
        msg.setExcludedUrls(excludedUrlsJson);
        return msg;
    }

    private boolean excluded(String url, TaskMessage msg) throws Exception {
        return (Boolean) isExcludedUrl.invoke(engine, url, msg);
    }

    @Nested
    @DisplayName("单前缀规则")
    class SinglePrefix {

        @Test
        @DisplayName("/tags/ 拦截 /tags 下的页面")
        void interceptsTagsPath() throws Exception {
            assertTrue(excluded("https://example.com/tags/foo", msg("[\"/tags/\"]")));
        }

        @Test
        @DisplayName("/tags/ 拦截 /tags 下的带参数页面")
        void interceptsTagsPathWithQuery() throws Exception {
            assertTrue(excluded("https://example.com/tags/foo?a=1&b=2", msg("[\"/tags/\"]")));
        }

        @Test
        @DisplayName("/tags/ 拦截 /tags 本身")
        void interceptsTagsPathExact() throws Exception {
            assertTrue(excluded("https://example.com/tags", msg("[\"/tags/\"]")));
        }

        @Test
        @DisplayName("/tags/ 不拦截 /tagx 路径")
        void doesNotInterceptSimilarPath() throws Exception {
            assertFalse(excluded("https://example.com/tagx/foo", msg("[\"/tags/\"]")));
        }

        @Test
        @DisplayName("/tags/ 不拦截 /tags2 路径")
        void doesNotInterceptTags2() throws Exception {
            assertFalse(excluded("https://example.com/tags2/foo", msg("[\"/tags/\"]")));
        }

        @Test
        @DisplayName("无前缀斜杠的规则也能工作")
        void prefixWithoutTrailingSlash() throws Exception {
            assertTrue(excluded("https://example.com/tags/foo", msg("[\"/tags\"]")));
        }

        @Test
        @DisplayName("文件排除规则匹配查询参数及嵌套路径中的 URL")
        void filePathRuleMatchesContainedUrl() throws Exception {
            TaskMessage rule = msg("[\"/wp-login.php\"]");

            assertTrue(excluded("https://example.com/wp-login.php?action=lostpassword", rule));
            assertTrue(excluded("https://example.com/admin/wp-login.php", rule));
            assertFalse(excluded("https://example.com/wp-login", rule));
        }
    }

    @Nested
    @DisplayName("多前缀规则")
    class MultiplePrefixes {

        @Test
        @DisplayName("两个独立前缀各自生效")
        void twoIndependent() throws Exception {
            TaskMessage m = msg("[\"/tags/\", \"/archive/\"]");
            assertTrue(excluded("https://example.com/tags/foo", m));
            assertTrue(excluded("https://example.com/archive/2024", m));
            assertFalse(excluded("https://example.com/news", m));
        }

        @Test
        @DisplayName("多个前缀：/a/b/c 不拦截 /a/b，但拦截 /a/b/c")
        void longerDoesNotBlockShorter() throws Exception {
            TaskMessage m = msg("[\"/a/b/c\"]");
            assertFalse(excluded("https://example.com/a/b", m));
            assertTrue(excluded("https://example.com/a/b/c", m));
        }

        @Test
        @DisplayName("多个前缀交叉：/tags 和 /tags/extra")
        void overlappingPrefixes() throws Exception {
            TaskMessage m = msg("[\"/tags\", \"/tags/extra\"]");
            assertTrue(excluded("https://example.com/tags/foo", m));
            assertTrue(excluded("https://example.com/tags/extra/foo", m));
            assertTrue(excluded("https://example.com/tags/extra", m));
            assertFalse(excluded("https://example.com/tagsx", m));
        }

        @Test
        @DisplayName("多个前缀含带参数的页面")
        void withQueryParams() throws Exception {
            TaskMessage m = msg("[\"/tags/\", \"/archive/2024/\"]");
            assertTrue(excluded("https://example.com/tags/foo?page=2&sort=desc", m));
            assertTrue(excluded("https://example.com/archive/2024/jan?v=1", m));
            assertFalse(excluded("https://example.com/archive/2025/jan?v=1", m));
        }

        @Test
        @DisplayName("前缀边界：/api/v2 不拦截 /api/v2beta")
        void prefixBoundary() throws Exception {
            TaskMessage m = msg("[\"/api/v2\"]");
            assertTrue(excluded("https://example.com/api/v2/users", m));
            assertFalse(excluded("https://example.com/api/v2beta/users", m));
        }

        @Test
        @DisplayName("前缀边界：/api/v2 不拦截 /api/v2x")
        void prefixBoundaryX() throws Exception {
            TaskMessage m = msg("[\"/api/v2\"]");
            assertFalse(excluded("https://example.com/api/v2x", m));
        }

        @Test
        @DisplayName("多个前缀同时含精确规则")
        void mixedWithExact() throws Exception {
            TaskMessage m = msg("[\"https://example.com/skip\", \"/tags/\", \"/archive/\"]");
            assertTrue(excluded("https://example.com/skip", m));
            assertTrue(excluded("https://example.com/tags/foo", m));
            assertTrue(excluded("https://example.com/archive/2024", m));
            assertFalse(excluded("https://example.com/other", m));
        }

        @Test
        @DisplayName("根路径前缀 / 拦截所有")
        void rootPrefix() throws Exception {
            TaskMessage m = msg("[\"/\"]");
            assertTrue(excluded("https://example.com/anything", m));
            assertTrue(excluded("https://example.com/a/b/c", m));
        }

        @Test
        @DisplayName("深层嵌套前缀 /a/b/c/d")
        void deepPrefix() throws Exception {
            TaskMessage m = msg("[\"/a/b/c/d\"]");
            assertTrue(excluded("https://example.com/a/b/c/d/e", m));
            assertTrue(excluded("https://example.com/a/b/c/d", m));
            assertFalse(excluded("https://example.com/a/b/c", m));
            assertFalse(excluded("https://example.com/a/b/c/dx", m));
        }
    }

    @Nested
    @DisplayName("精确 URL 规则")
    class ExactRules {

        @Test
        @DisplayName("完整 URL 精确匹配")
        void exactMatch() throws Exception {
            assertTrue(excluded("https://example.com/page", msg("[\"https://example.com/page\"]")));
        }

        @Test
        @DisplayName("完整 URL 不拦截带参数的变体")
        void exactDoesNotMatchWithQuery() throws Exception {
            assertFalse(excluded("https://example.com/page?a=1", msg("[\"https://example.com/page\"]")));
        }

        @Test
        @DisplayName("忽略大小写和默认端口")
        void normalizesCaseAndPort() throws Exception {
            assertTrue(excluded("http://EXAMPLE.com/page", msg("[\"http://example.com:80/page\"]")));
        }
    }

    @Nested
    @DisplayName("extractPathAndQuery")
    class ExtractPathAndQuery {

        private String extract(String url) throws Exception {
            return (String) extractPathAndQuery.invoke(engine, url);
        }

        @Test
        void basic() throws Exception {
            assertEquals("/tags/foo?a=1", extract("https://example.com/tags/foo?a=1"));
        }

        @Test
        void rootPath() throws Exception {
            assertEquals("/", extract("https://example.com"));
        }

        @Test
        void pathOnly() throws Exception {
            assertEquals("/a/b/c", extract("https://example.com/a/b/c"));
        }

        @Test
        void withPort() throws Exception {
            assertEquals("/x", extract("https://example.com:8080/x"));
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("无排除规则时不拦截任何 URL")
        void noRules() throws Exception {
            TaskMessage m = msg(null);
            assertFalse(excluded("https://example.com/anything", m));
        }

        @Test
        @DisplayName("空数组不拦截")
        void emptyArray() throws Exception {
            TaskMessage m = msg("[]");
            assertFalse(excluded("https://example.com/anything", m));
        }

        @Test
        @DisplayName("非 http 协议 URL 不拦截")
        void nonHttpUrl() throws Exception {
            TaskMessage m = msg("[\"/tags/\"]");
            assertFalse(excluded("ftp://example.com/tags/foo", m));
        }

        @Test
        @DisplayName("重复规则不报错")
        void duplicateRules() throws Exception {
            TaskMessage m = msg("[\"/tags/\", \"/tags/\"]");
            assertTrue(excluded("https://example.com/tags/foo", m));
        }

        @Test
        @DisplayName("空白规则被跳过")
        void blankRule() throws Exception {
            TaskMessage m = msg("[\"\", \"  \", \"/tags/\"]");
            assertTrue(excluded("https://example.com/tags/foo", m));
            assertFalse(excluded("https://example.com/other", m));
        }
    }
}
