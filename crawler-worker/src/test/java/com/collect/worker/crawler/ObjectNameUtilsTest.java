package com.collect.worker.crawler;

import com.collect.common.util.ObjectNameUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ObjectNameUtilsTest {

    @Test
    void generatesFixedLengthMd5HexWithoutUrlUnsafeChars() {
        String value = "https://example.com/路径?a=1&b=2";

        String encoded = ObjectNameUtils.hashUrl(value);

        assertEquals(32, encoded.length());
        assertFalse(encoded.contains("/"));
        assertFalse(encoded.contains("+"));
        assertFalse(encoded.contains("="));
        assertEquals(encoded, ObjectNameUtils.hashUrl(value));
        assertNotEquals(encoded, ObjectNameUtils.hashUrl(value + "?t=1"));
    }
}