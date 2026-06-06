package com.example.netty;

import com.google.gson.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Netty TCP 客户端。
 *
 * 线程安全：使用 ConcurrentHashMap + AtomicInteger _reqId 实现请求-响应关联，
 * 多线程并发调用 send() 时各自获得对应响应，互不干扰。
 */
public class NettyClient {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>) (src, type, ctx) ->
                            new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonDeserializer<LocalDateTime>) (json, type, ctx) ->
                            LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .create();

    private final String host;
    private final int port;
    private Channel channel;
    private EventLoopGroup group;

    // 请求-响应关联：_reqId → CompletableFuture
    private final ConcurrentHashMap<Integer, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicInteger reqIdCounter = new AtomicInteger(0);

    public NettyClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws Exception {
        group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline()
                                .addLast(new LineBasedFrameDecoder(10485760))
                                .addLast(new StringDecoder(StandardCharsets.UTF_8))
                                .addLast(new StringEncoder(StandardCharsets.UTF_8))
                                .addLast(new ClientHandler(pendingRequests));
                    }
                });

        channel = bootstrap.connect(host, port).sync().channel();
    }

    /**
     * 发送请求并阻塞等待响应（10秒超时）。
     *
     * 线程安全：每个请求分配唯一 _reqId，通过 ConcurrentHashMap
     * 将响应路由回正确的调用线程。
     */
    public Response send(String action, JsonObject params) throws Exception {
        int reqId = reqIdCounter.incrementAndGet();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put(reqId, future);

        try {
            Request req = new Request();
            req.setAction(action);
            req.setParams(params);
            req.set_reqId(reqId);

            channel.writeAndFlush(GSON.toJson(req) + "\n");
            String responseJson = future.get(10, TimeUnit.SECONDS);
            return GSON.fromJson(responseJson, Response.class);
        } finally {
            pendingRequests.remove(reqId);
        }
    }

    public void close() {
        if (channel != null) channel.close();
        if (group != null) group.shutdownGracefully();
    }
}
