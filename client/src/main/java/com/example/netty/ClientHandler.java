package com.example.netty;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端 ChannelHandler — 共享实例，通过 _reqId 将响应路由到正确的调用者。
 */
public class ClientHandler extends SimpleChannelInboundHandler<String> {

    private static final Gson GSON = new Gson();

    private final ConcurrentHashMap<Integer, CompletableFuture<String>> pendingRequests;

    public ClientHandler(ConcurrentHashMap<Integer, CompletableFuture<String>> pendingRequests) {
        this.pendingRequests = pendingRequests;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            JsonObject json = JsonParser.parseString(msg).getAsJsonObject();
            int reqId = json.has("_reqId") ? json.get("_reqId").getAsInt() : -1;
            CompletableFuture<String> future = pendingRequests.remove(reqId);
            if (future != null) {
                future.complete(msg);
            }
        } catch (Exception e) {
            // 无法解析的响应 — 忽略（或记录日志）
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
