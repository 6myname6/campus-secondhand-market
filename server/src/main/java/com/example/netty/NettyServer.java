package com.example.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Netty TCP 服务器。
 *
 * Boss 线程组(1线程)接收连接，Worker 线程组处理 I/O 读写。
 * ServerHandler 通过 businessGroup 在独立业务线程池中执行，
 * 避免数据库操作阻塞 Netty 的 I/O 线程。
 */
public class NettyServer {

    private static final Logger log = LoggerFactory.getLogger(NettyServer.class);

    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    /**
     * 业务线程池 — ServerHandler 中的数据库操作在此线程池执行。
     * 线程数默认 16，可根据实际并发量调整。
     */
    private EventExecutorGroup businessGroup;

    private final CountDownLatch readyLatch;

    public NettyServer(int port) {
        this(port, false);
    }

    public NettyServer(int port, boolean useLatch) {
        this.port = port;
        this.readyLatch = useLatch ? new CountDownLatch(1) : null;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        businessGroup = new DefaultEventExecutorGroup(16);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                                    .addLast(new LineBasedFrameDecoder(10485760))
                                    .addLast(new StringDecoder(Charset.forName("UTF-8")))
                                    .addLast(new StringEncoder(Charset.forName("UTF-8")))
                                    // ServerHandler 在 businessGroup 中执行，不阻塞 I/O 线程
                                    .addLast(businessGroup, "serverHandler", new ServerHandler());
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("NettyServer started on port {}", port);
            if (readyLatch != null) readyLatch.countDown();
            future.channel().closeFuture().await();
        } finally {
            shutdown();
        }
    }

    public void awaitReady() throws InterruptedException {
        if (readyLatch != null) readyLatch.await();
    }

    public void shutdown() {
        log.info("NettyServer shutting down...");
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (businessGroup != null) businessGroup.shutdownGracefully();
    }

    public static void main(String[] args) throws InterruptedException {
        NettyServer server = new NettyServer(8081);
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        server.start();
    }
}
