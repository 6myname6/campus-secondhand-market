package com.example.netty;

import com.google.gson.JsonObject;

import java.math.BigDecimal;

public final class ParamValidator {

    private ParamValidator() {}

    public static void require(JsonObject params, String key) {
        if (params == null || !params.has(key) || params.get(key).isJsonNull()) {
            throw new IllegalArgumentException("缺少必要参数: " + key);
        }
    }

    public static int requireInt(JsonObject params, String key) {
        require(params, key);
        try {
            return params.get(key).getAsInt();
        } catch (Exception e) {
            throw new IllegalArgumentException(key + " 必须是整数");
        }
    }

    public static String requireStr(JsonObject params, String key) {
        require(params, key);
        try {
            return params.get(key).getAsString();
        } catch (Exception e) {
            throw new IllegalArgumentException(key + " 必须是字符串");
        }
    }

    public static int requirePositiveInt(JsonObject params, String key) {
        int val = requireInt(params, key);
        if (val <= 0) throw new IllegalArgumentException(key + " 必须大于0");
        return val;
    }

    public static BigDecimal requireBigDecimal(JsonObject params, String key) {
        require(params, key);
        try {
            BigDecimal val = new BigDecimal(params.get(key).getAsString());
            if (val.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException(key + " 不能为负数");
            return val;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " 不是有效的金额");
        }
    }

    public static void requireStringLength(JsonObject params, String key, int maxLen) {
        String val = requireStr(params, key);
        if (val.length() > maxLen) throw new IllegalArgumentException(key + " 长度不能超过" + maxLen + "个字符");
    }
}
