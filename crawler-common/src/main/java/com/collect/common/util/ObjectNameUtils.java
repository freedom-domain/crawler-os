package com.collect.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ObjectNameUtils {

    private ObjectNameUtils() {
    }

    public static String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
