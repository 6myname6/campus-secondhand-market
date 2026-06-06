package com.example.service;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Token 认证服务。
 *
 * 基于 ConcurrentHashMap 的内存 Token 管理：
 * - 每次 login 生成全新 Token，后登录挤掉前登录的旧 Token
 * - validate 校验 Token 有效性并返回 userId
 * - logout 主动销毁 Token
 * - 定时清理过期 Token（默认 30 分钟无活动失效）
 */
public class AuthService {

    // Token 过期时间：30 分钟（毫秒）
    private static final long TOKEN_TTL_MS = 30 * 60 * 1000L;

    private final Map<String, TokenEntry> tokenToUserId = new ConcurrentHashMap<>();
    private final Map<Integer, String> userIdToToken = new ConcurrentHashMap<>();

    public AuthService() {
        // 每 5 分钟清理一次过期 Token
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleWithFixedDelay(this::cleanExpiredTokens, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * 用户登录 — 生成新 Token，挤掉旧 Token。
     *
     * 后登录挤掉前登录：如果该用户已有活跃 Token，先删除旧映射，
     * 再生成新 Token。旧 Token 立即失效。
     */
    public String login(int userId) {
        // 挤掉旧 Token
        String oldToken = userIdToToken.remove(userId);
        if (oldToken != null) {
            tokenToUserId.remove(oldToken);
        }
        // 生成新 Token
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenToUserId.put(token, new TokenEntry(userId));
        userIdToToken.put(userId, token);
        return token;
    }

    /**
     * 校验 Token 有效性，刷新最后访问时间。
     *
     * @return 对应的 userId；Token 无效或已过期返回 null
     */
    public Integer validate(String token) {
        if (token == null) return null;
        TokenEntry entry = tokenToUserId.get(token);
        if (entry == null) return null;
        if (entry.isExpired()) {
            // 过期 — 清理映射后返回 null
            logout(token);
            return null;
        }
        // 刷新最后访问时间
        entry.refresh();
        return entry.userId;
    }

    /**
     * 登出 — 销毁 Token。
     */
    public void logout(String token) {
        TokenEntry entry = tokenToUserId.remove(token);
        if (entry != null) {
            userIdToToken.remove(entry.userId);
        }
    }

    /** 内部定时清理过期 Token */
    private void cleanExpiredTokens() {
        Iterator<Map.Entry<String, TokenEntry>> it = tokenToUserId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TokenEntry> e = it.next();
            if (e.getValue().isExpired()) {
                it.remove();
                userIdToToken.remove(e.getValue().userId);
            }
        }
    }

    /** Token 条目，包含 userId 和最后访问时间 */
    private static class TokenEntry {
        final int userId;
        volatile long lastAccessTime;

        TokenEntry(int userId) {
            this.userId = userId;
            this.lastAccessTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - lastAccessTime > TOKEN_TTL_MS;
        }

        void refresh() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
}
