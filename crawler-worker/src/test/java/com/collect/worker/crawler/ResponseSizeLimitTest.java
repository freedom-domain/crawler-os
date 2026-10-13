package com.collect.worker.crawler;

import okio.Buffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResponseSizeLimitTest {

    @Test
    void readBodyWithinLimit_shouldAcceptBodyAtLimit() throws Exception {
        Buffer source = new Buffer().writeUtf8("1234");

        Buffer body = CrawlerEngine.readBodyWithinLimit(source, 4);

        assertEquals("1234", body.readUtf8());
    }

    @Test
    void readBodyWithinLimit_shouldRejectBodyOverLimit() {
        Buffer source = new Buffer().writeUtf8("12345");

        assertThrows(IllegalStateException.class,
                () -> CrawlerEngine.readBodyWithinLimit(source, 4));
    }
}
