package com.example.netty;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class NettyE2ETest {

    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        System.out.println("========== Netty 端到端测试开始 ==========\n");

        NettyServer server = new NettyServer(8082, true);
        Thread serverThread = new Thread(() -> {
            try { server.start(); } catch (InterruptedException ignored) {}
        });
        serverThread.setDaemon(true);
        serverThread.start();
        server.awaitReady();

        NettyClient client = new NettyClient("127.0.0.1", 8082);
        try {
            client.connect();
            System.out.println("客户端已连接\n");

            testUserService(client);
            testGoodsService(client);
            testCategoryService(client);
            testGoodsImageService(client);
            testFavoriteService(client);
            testOrderService(client);
            testLoginAndChat(client);

            System.out.println("========== 全部端到端测试通过 ==========");
        } finally {
            client.close();
        }
    }

    // ==================== UserService ====================
    static void testUserService(NettyClient client) throws Exception {
        System.out.println("--- UserService ---");

        JsonObject p = new JsonObject();
        p.addProperty("username", "nett_user");
        p.addProperty("password", "123456");
        p.addProperty("phone", "13800000001");
        p.addProperty("email", "test@example.com");
        Response resp = client.send("UserService.register", p);
        assert resp.isSuccess() : "注册失败: " + resp.getMessage();
        int userId = ((Double) resp.getData()).intValue();
        System.out.println("  register  userId=" + userId + " ✓");

        p = new JsonObject(); p.addProperty("username", "nett_user");
        resp = client.send("UserService.findByUsername", p);
        assert resp.isSuccess() && resp.getData() != null;
        System.out.println("  findByUsername ✓");

        p = new JsonObject(); p.addProperty("userId", userId);
        resp = client.send("UserService.findById", p);
        assert resp.isSuccess();
        System.out.println("  findById ✓");

        p = new JsonObject(); p.addProperty("userId", userId); p.addProperty("status", 0);
        resp = client.send("UserService.updateStatus", p);
        assert resp.isSuccess();
        System.out.println("  updateStatus ✓");

        p = new JsonObject(); p.addProperty("userId", userId);
        p.addProperty("phone", "13800000002"); p.addProperty("email", "updated@x.com");
        resp = client.send("UserService.updateProfile", p);
        assert resp.isSuccess();
        System.out.println("  updateProfile ✓");

        p = new JsonObject(); p.addProperty("page", 1); p.addProperty("pageSize", 5);
        resp = client.send("UserService.listUsers", p);
        assert resp.isSuccess();
        System.out.println("  listUsers ✓");

        resp = client.send("UserService.countUsers", new JsonObject());
        assert resp.isSuccess();
        System.out.println("  countUsers ✓\n");
    }

    // ==================== GoodsService ====================
    static void testGoodsService(NettyClient client) throws Exception {
        System.out.println("--- GoodsService ---");

        JsonObject p = new JsonObject();
        p.addProperty("username", "seller_nett2"); p.addProperty("password", "123456");
        int sellerId = ((Double) client.send("UserService.register", p).getData()).intValue();

        p = new JsonObject(); p.addProperty("sellerId", sellerId);
        p.addProperty("categoryId", 1); p.addProperty("title", "Netty商品");
        p.addProperty("description", "测试"); p.addProperty("price", "199.99");
        p.addProperty("originalPrice", "299.99");
        int goodsId = ((Double) client.send("GoodsService.publish", p).getData()).intValue();
        System.out.println("  publish  goodsId=" + goodsId + " ✓");

        p = new JsonObject(); p.addProperty("goodsId", goodsId);
        assert client.send("GoodsService.findById", p).isSuccess();
        System.out.println("  findById ✓");

        p = new JsonObject(); p.addProperty("goodsId", goodsId);
        p.addProperty("title", "更新标题"); p.addProperty("description", "更新描述");
        p.addProperty("price", "159.99"); p.addProperty("status", 1);
        assert client.send("GoodsService.updateGoods", p).isSuccess();
        System.out.println("  updateGoods ✓");

        p = new JsonObject(); p.addProperty("categoryId", 1);
        p.addProperty("page", 1); p.addProperty("pageSize", 10);
        assert client.send("GoodsService.findByCategory", p).isSuccess();
        System.out.println("  findByCategory ✓");

        p = new JsonObject(); p.addProperty("keyword", "Netty");
        p.addProperty("page", 1); p.addProperty("pageSize", 10);
        assert client.send("GoodsService.search", p).isSuccess();
        System.out.println("  search ✓");

        p = new JsonObject(); p.addProperty("sellerId", sellerId);
        p.addProperty("page", 1); p.addProperty("pageSize", 10);
        assert client.send("GoodsService.listBySeller", p).isSuccess();
        System.out.println("  listBySeller ✓");

        p = new JsonObject(); p.addProperty("goodsId", goodsId); p.addProperty("sellerId", sellerId);
        assert client.send("GoodsService.offShelf", p).isSuccess();
        System.out.println("  offShelf ✓\n");
    }

    // ==================== CategoryService ====================
    static void testCategoryService(NettyClient client) throws Exception {
        System.out.println("--- CategoryService ---");

        JsonObject p = new JsonObject();
        p.addProperty("name", "图书"); p.addProperty("parentId", 0); p.addProperty("sortOrder", 1);
        int catId = ((Double) client.send("CategoryService.create", p).getData()).intValue();
        System.out.println("  create  catId=" + catId + " ✓");

        p = new JsonObject(); p.addProperty("categoryId", catId);
        assert client.send("CategoryService.findById", p).isSuccess();
        System.out.println("  findById ✓");

        assert client.send("CategoryService.findAll", new JsonObject()).isSuccess();
        System.out.println("  findAll ✓");

        p = new JsonObject(); p.addProperty("parentId", 0);
        assert client.send("CategoryService.findByParent", p).isSuccess();
        System.out.println("  findByParent ✓");

        p = new JsonObject(); p.addProperty("categoryId", catId);
        p.addProperty("name", "电子书"); p.addProperty("sortOrder", 2);
        assert client.send("CategoryService.update", p).isSuccess();
        System.out.println("  update ✓");

        p = new JsonObject(); p.addProperty("categoryId", catId);
        assert client.send("CategoryService.delete", p).isSuccess();
        System.out.println("  delete ✓\n");
    }

    // ==================== GoodsImageService ====================
    static void testGoodsImageService(NettyClient client) throws Exception {
        System.out.println("--- GoodsImageService ---");

        // 准备商品
        JsonObject p = new JsonObject();
        p.addProperty("username", "img_seller"); p.addProperty("password", "123");
        int sellerId = ((Double) client.send("UserService.register", p).getData()).intValue();
        p = new JsonObject(); p.addProperty("sellerId", sellerId);
        p.addProperty("categoryId", 1); p.addProperty("title", "有图商品");
        p.addProperty("price", "99.00");
        int goodsId = ((Double) client.send("GoodsService.publish", p).getData()).intValue();

        p = new JsonObject(); p.addProperty("goodsId", goodsId);
        p.addProperty("imageUrl", "/images/goods/1.jpg"); p.addProperty("sortOrder", 0);
        int imgId = ((Double) client.send("GoodsImageService.add", p).getData()).intValue();
        System.out.println("  add  imgId=" + imgId + " ✓");

        p = new JsonObject(); p.addProperty("goodsId", goodsId);
        assert client.send("GoodsImageService.findByGoods", p).isSuccess();
        System.out.println("  findByGoods ✓");

        p = new JsonObject(); p.addProperty("imageId", imgId);
        assert client.send("GoodsImageService.delete", p).isSuccess();
        System.out.println("  delete ✓\n");
    }

    // ==================== FavoriteService ====================
    static void testFavoriteService(NettyClient client) throws Exception {
        System.out.println("--- FavoriteService ---");

        JsonObject p = new JsonObject();
        p.addProperty("username", "fav_user2"); p.addProperty("password", "123");
        int userId = ((Double) client.send("UserService.register", p).getData()).intValue();

        p = new JsonObject(); p.addProperty("userId", userId); p.addProperty("goodsId", 1);
        int favId = ((Double) client.send("FavoriteService.add", p).getData()).intValue();
        System.out.println("  add  favId=" + favId + " ✓");

        assert client.send("FavoriteService.listByUser", p).isSuccess();
        System.out.println("  listByUser ✓");

        p = new JsonObject(); p.addProperty("userId", userId); p.addProperty("goodsId", 1);
        assert client.send("FavoriteService.check", p).isSuccess();
        System.out.println("  check ✓");

        p = new JsonObject(); p.addProperty("goodsId", 1);
        assert client.send("FavoriteService.countByGoods", p).isSuccess();
        System.out.println("  countByGoods ✓");

        p = new JsonObject(); p.addProperty("userId", userId); p.addProperty("goodsId", 1);
        assert client.send("FavoriteService.remove", p).isSuccess();
        System.out.println("  remove ✓\n");
    }

    // ==================== OrderService ====================
    static void testOrderService(NettyClient client) throws Exception {
        System.out.println("--- OrderService ---");

        JsonObject p = new JsonObject();
        p.addProperty("username", "buyer_nett2"); p.addProperty("password", "123");
        int buyerId = ((Double) client.send("UserService.register", p).getData()).intValue();
        p = new JsonObject(); p.addProperty("username", "seller_ord"); p.addProperty("password", "123");
        int sellerId = ((Double) client.send("UserService.register", p).getData()).intValue();
        p = new JsonObject(); p.addProperty("sellerId", sellerId);
        p.addProperty("categoryId", 1); p.addProperty("title", "订单商品"); p.addProperty("price", "99.00");
        int goodsId = ((Double) client.send("GoodsService.publish", p).getData()).intValue();

        String itemsJson = "[{\"goodsId\":" + goodsId + ",\"price\":\"99.00\",\"quantity\":1}]";
        p = new JsonObject(); p.addProperty("buyerId", buyerId);
        p.addProperty("receiverName", "李四"); p.addProperty("receiverPhone", "13800000000");
        p.addProperty("address", "上海市"); p.addProperty("buyerNote", "请发货");
        p.add("items", GSON.fromJson(itemsJson, com.google.gson.JsonArray.class));
        int orderId = ((Double) client.send("OrderService.createOrder", p).getData()).intValue();
        System.out.println("  createOrder  orderId=" + orderId + " ✓");

        p = new JsonObject(); p.addProperty("orderId", orderId); p.addProperty("buyerId", buyerId);
        assert client.send("OrderService.pay", p).isSuccess();
        System.out.println("  pay ✓");

        p = new JsonObject(); p.addProperty("orderId", orderId);
        p.addProperty("logisticsCompany", "顺丰"); p.addProperty("logisticsNo", "SF123");
        assert client.send("OrderService.ship", p).isSuccess();
        System.out.println("  ship ✓");

        p = new JsonObject(); p.addProperty("orderId", orderId); p.addProperty("buyerId", buyerId);
        assert client.send("OrderService.complete", p).isSuccess();
        System.out.println("  complete ✓");

        p = new JsonObject(); p.addProperty("buyerId", buyerId);
        p.addProperty("page", 1); p.addProperty("pageSize", 10);
        assert client.send("OrderService.listByBuyer", p).isSuccess();
        System.out.println("  listByBuyer ✓\n");
    }

    // ==================== Login & Chat ====================
    static void testLoginAndChat(NettyClient client) throws Exception {
        System.out.println("--- Login & Chat ---");

        // 注册两个用户
        JsonObject p = new JsonObject();
        p.addProperty("username", "chat_user_a"); p.addProperty("password", "pass_a");
        int user1Id = ((Double) client.send("UserService.register", p).getData()).intValue();
        p = new JsonObject();
        p.addProperty("username", "chat_user_b"); p.addProperty("password", "pass_b");
        int user2Id = ((Double) client.send("UserService.register", p).getData()).intValue();

        // 登录
        p = new JsonObject(); p.addProperty("username", "chat_user_a"); p.addProperty("password", "pass_a");
        Response resp = client.send("UserService.login", p);
        assert resp.isSuccess() : "登录失败: " + resp.getMessage();
        String token = (String) ((com.google.gson.JsonObject)
                GSON.toJsonTree(resp.getData()).getAsJsonObject()).get("token").getAsString();
        System.out.println("  login  token=" + token.substring(0, 8) + "... ✓");

        // 创建会话（需认证）
        p = new JsonObject(); p.addProperty("userId1", user1Id); p.addProperty("userId2", user2Id);
        p.addProperty("token", token);
        int convId = ((Double) client.send("ConversationService.createOrGet", p).getData()).intValue();
        System.out.println("  createOrGet  convId=" + convId + " ✓");

        // 发送消息
        p = new JsonObject(); p.addProperty("conversationId", convId);
        p.addProperty("senderId", user1Id); p.addProperty("receiverId", user2Id);
        p.addProperty("content", "你好！我对你的商品感兴趣");
        p.addProperty("token", token);
        int msgId = ((Double) client.send("MessageService.send", p).getData()).intValue();
        System.out.println("  send  msgId=" + msgId + " ✓");

        // 查消息列表
        p = new JsonObject(); p.addProperty("conversationId", convId);
        p.addProperty("token", token);
        p.addProperty("page", 1); p.addProperty("pageSize", 20);
        assert client.send("MessageService.listByConversation", p).isSuccess();
        System.out.println("  listByConversation ✓");

        // 标记已读
        p = new JsonObject(); p.addProperty("conversationId", convId);
        p.addProperty("userId", user2Id); p.addProperty("token", token);
        assert client.send("MessageService.markRead", p).isSuccess();
        System.out.println("  markRead ✓");

        // 未读数
        p = new JsonObject(); p.addProperty("userId", user2Id); p.addProperty("token", token);
        assert client.send("MessageService.countUnread", p).isSuccess();
        System.out.println("  countUnread ✓");

        // 未登录应拒绝（无 token）
        p = new JsonObject(); p.addProperty("userId", user1Id);
        resp = client.send("MessageService.countUnread", p);
        assert !resp.isSuccess() && resp.getMessage().contains("登录") : "应提示未登录";
        System.out.println("  auth拦截 (无token) ✓\n");
    }
}
