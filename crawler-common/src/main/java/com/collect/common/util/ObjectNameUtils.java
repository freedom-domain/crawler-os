package com.collect.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ObjectNameUtils {

    private ObjectNameUtils() {
    }

    /**
     * 基于 URL/资源地址生成 MinIO 对象名使用的哈希片段。
     * 使用 MD5 十六进制字符串，长度固定且仅包含 0-9/a-f，适合做对象名。
     */
    public static String hashUrl(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("生成 URL 哈希失败", e);
        }
    }
}
