package com.collect.worker.crawler;

import com.collect.common.util.ObjectNameUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ObjectNameUtilsTest {

    @Test
    void encodesUtf8AsFilenameSafeBase64WithoutPadding() {
        String value = "https://example.com/路径?a=1&b=2";

        String encoded = ObjectNameUtils.base64Url(value);

        assertEquals(value, new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
        assertFalse(encoded.contains("/"));
        assertFalse(encoded.contains("+"));
        assertFalse(encoded.contains("="));
    }
}
