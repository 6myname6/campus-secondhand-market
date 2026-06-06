package com.example.swing;

import com.example.netty.NettyClient;
import com.example.netty.Response;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Objects;

/**
 * API 门面 — 封装所有服务端调用为类型化 Java 方法。
 *
 * 使用带 LocalDateTime 适配器的 Gson，确保时间字段正确序列化。
 * 通过数值安全转换方法避免 Double→Integer 的类型问题。
 */
public class ApiClient {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>) (src, type, ctx) ->
                            new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonDeserializer<LocalDateTime>) (json, type, ctx) ->
                            LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .create();

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final Type LIST_MAP_TYPE = new TypeToken<List<Map<String, Object>>>() {}.getType();

    private NettyClient client;
    private final AppContext ctx = AppContext.getInstance();

    // 用户名缓存
    private final Map<Integer, String> usernameCache = new HashMap<>();

    public void connect(String host, int port) throws Exception {
        client = new NettyClient(host, port);
        client.connect();
    }

    public void close() {
        if (client != null) client.close();
    }

    private Response send(String action, JsonObject params) throws Exception {
        return client.send(action, params);
    }

    private JsonElement toJsonElement(Object obj) {
        return GSON.toJsonTree(obj);
    }

    /** 安全地将 Object 转为 int（处理 Gson 的 Double 类型） */
    private static int toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        return 0;
    }

    /** 安全地将 Object 转为 long */
    @SuppressWarnings("unused")
    private static long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Map<String, Object> map, String key) {
        return (T) map.get(key);
    }

    // ==================== Auth ====================

    public Map<String, Object> login(String username, String password) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("username", username);
        p.addProperty("password", password);
        Response r = send("user.login", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        Map<String, Object> result = GSON.fromJson(toJsonElement(r.getData()), MAP_TYPE);
        Map<String, Object> user = get(result, "user");
        ctx.login(toInt(user.get("userId")), (String) user.get("username"),
                (String) result.get("token"), Objects.toString(user.get("role"), "user"));
        return result;
    }

    public int register(String username, String password, String phone, String email) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("username", username);
        p.addProperty("password", password);
        if (phone != null && !phone.isEmpty()) p.addProperty("phone", phone);
        if (email != null && !email.isEmpty()) p.addProperty("email", email);
        Response r = send("user.register", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return toInt(r.getData());
    }

    // ==================== Category ====================

    public List<Map<String, Object>> getCategories() throws Exception {
        Response r = send("category.findAll", new JsonObject());
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    // ==================== Goods ====================

    public List<Map<String, Object>> getGoodsByCategory(int categoryId, int page) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("categoryId", categoryId);
        p.addProperty("page", page);
        p.addProperty("pageSize", 20);
        Response r = send("goods.findByCategory", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    public List<Map<String, Object>> searchGoods(String keyword, int page) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("keyword", keyword);
        p.addProperty("page", page);
        p.addProperty("pageSize", 20);
        Response r = send("goods.search", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    public Map<String, Object> getGoodsDetail(int goodsId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("goodsId", goodsId);
        Response r = send("goods.findById", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), MAP_TYPE);
    }

    public int publishGoods(int sellerId, int categoryId, String title, String description,
                            BigDecimal price, BigDecimal originalPrice) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("sellerId", sellerId);
        p.addProperty("categoryId", categoryId);
        p.addProperty("title", title);
        if (description != null) p.addProperty("description", description);
        p.addProperty("price", price.toString());
        if (originalPrice != null) p.addProperty("originalPrice", originalPrice.toString());
        p.addProperty("token", ctx.getToken());
        Response r = send("goods.publish", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return toInt(r.getData());
    }

    public List<Map<String, Object>> getSellerGoods(int sellerId, int page) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("sellerId", sellerId);
        p.addProperty("page", page);
        p.addProperty("pageSize", 20);
        p.addProperty("token", ctx.getToken());
        Response r = send("goods.listBySeller", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    public void offShelf(int goodsId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("goodsId", goodsId);
        p.addProperty("token", ctx.getToken());
        Response r = send("goods.offShelf", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public void updateGoods(int goodsId, String title, String description,
                            BigDecimal price, Integer status) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("goodsId", goodsId);
        if (title != null) p.addProperty("title", title);
        if (description != null) p.addProperty("description", description);
        if (price != null) p.addProperty("price", price.toString());
        if (status != null) p.addProperty("status", status);
        p.addProperty("token", ctx.getToken());
        Response r = send("goods.update", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    // ==================== Goods Images ====================

    public List<Map<String, Object>> getGoodsImages(int goodsId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("goodsId", goodsId);
        Response r = send("goodsImage.findByGoods", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    public void addGoodsImage(int goodsId, String imageUrl, int sortOrder) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("goodsId", goodsId);
        p.addProperty("imageUrl", imageUrl);
        p.addProperty("sortOrder", sortOrder);
        p.addProperty("token", ctx.getToken());
        Response r = send("goodsImage.add", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    // ==================== File Upload/Download ====================

    public Map<String, String> uploadImage(String base64, String fileName) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("base64", base64);
        p.addProperty("fileName", fileName);
        p.addProperty("token", ctx.getToken());
        Response r = send("file.upload", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        Map<String, Object> data = GSON.fromJson(toJsonElement(r.getData()), MAP_TYPE);
        return Map.of("url", (String) data.get("url"));
    }

    public Map<String, String> downloadImage(String path) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("path", path);
        Response r = send("file.download", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        Map<String, Object> data = GSON.fromJson(toJsonElement(r.getData()), MAP_TYPE);
        return Map.of("base64", (String) data.get("base64"), "contentType", (String) data.get("contentType"));
    }

    // ==================== Order ====================

    public int createOrder(int buyerId, String receiverName, String receiverPhone,
                           String address, String buyerNote, List<Map<String, Object>> items) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("buyerId", buyerId);
        p.addProperty("receiverName", receiverName);
        p.addProperty("receiverPhone", receiverPhone);
        p.addProperty("address", address);
        if (buyerNote != null) p.addProperty("buyerNote", buyerNote);
        p.add("items", GSON.toJsonTree(items));
        p.addProperty("token", ctx.getToken());
        Response r = send("order.create", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return toInt(r.getData());
    }

    public List<Map<String, Object>> getBuyerOrders(int buyerId, int page) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("buyerId", buyerId);
        p.addProperty("page", page);
        p.addProperty("pageSize", 20);
        p.addProperty("token", ctx.getToken());
        Response r = send("order.listByBuyer", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    public List<Map<String, Object>> getSellerOrders(int sellerId, int page) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("sellerId", sellerId);
        p.addProperty("page", page);
        p.addProperty("pageSize", 20);
        p.addProperty("token", ctx.getToken());
        Response r = send("order.listBySeller", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    public Map<String, Object> getOrderDetail(int orderId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("orderId", orderId);
        p.addProperty("token", ctx.getToken());
        Response r = send("order.findById", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), MAP_TYPE);
    }

    public List<Map<String, Object>> getOrderItems(int orderId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("orderId", orderId);
        p.addProperty("token", ctx.getToken());
        Response r = send("order.findItems", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    public void payOrder(int orderId, int buyerId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("orderId", orderId);
        p.addProperty("buyerId", buyerId);
        p.addProperty("token", ctx.getToken());
        Response r = send("order.pay", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public void shipOrder(int orderId, String company, String logisticsNo) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("orderId", orderId);
        p.addProperty("logisticsCompany", company);
        p.addProperty("logisticsNo", logisticsNo);
        p.addProperty("token", ctx.getToken());
        Response r = send("order.ship", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public void completeOrder(int orderId, int buyerId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("orderId", orderId);
        p.addProperty("buyerId", buyerId);
        p.addProperty("token", ctx.getToken());
        Response r = send("order.complete", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public void cancelOrder(int orderId, int buyerId, String reason) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("orderId", orderId);
        p.addProperty("buyerId", buyerId);
        if (reason != null) p.addProperty("reason", reason);
        p.addProperty("token", ctx.getToken());
        Response r = send("order.cancel", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    // ==================== Favorite ====================

    public List<Map<String, Object>> getFavorites(int userId, int page) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("userId", userId);
        p.addProperty("page", page);
        p.addProperty("pageSize", 20);
        p.addProperty("token", ctx.getToken());
        Response r = send("favorite.listByUser", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    public void addFavorite(int userId, int goodsId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("userId", userId);
        p.addProperty("goodsId", goodsId);
        p.addProperty("token", ctx.getToken());
        Response r = send("favorite.add", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public void removeFavorite(int userId, int goodsId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("userId", userId);
        p.addProperty("goodsId", goodsId);
        p.addProperty("token", ctx.getToken());
        Response r = send("favorite.remove", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public boolean checkFavorite(int userId, int goodsId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("userId", userId);
        p.addProperty("goodsId", goodsId);
        p.addProperty("token", ctx.getToken());
        Response r = send("favorite.check", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return (Boolean) r.getData();
    }

    public long getFavoriteCount(int goodsId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("goodsId", goodsId);
        Response r = send("favorite.countByGoods", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return ((Number) r.getData()).longValue();
    }

    // ==================== Conversation ====================

    public List<Map<String, Object>> getConversations(int userId, int page) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("userId", userId);
        p.addProperty("page", page);
        p.addProperty("pageSize", 20);
        p.addProperty("token", ctx.getToken());
        Response r = send("conversation.listByUser", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    public int getOrCreateConv(int userId1, int userId2) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("userId1", userId1);
        p.addProperty("userId2", userId2);
        p.addProperty("token", ctx.getToken());
        Response r = send("conversation.createOrGet", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return toInt(r.getData());
    }

    public void markConversationRead(int conversationId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("conversationId", conversationId);
        p.addProperty("token", ctx.getToken());
        Response r = send("conversation.markRead", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    // ==================== Message ====================

    public List<Map<String, Object>> getMessages(int conversationId, int page) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("conversationId", conversationId);
        p.addProperty("page", page);
        p.addProperty("pageSize", 50);
        p.addProperty("token", ctx.getToken());
        Response r = send("message.listByConversation", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    public void sendMessage(int conversationId, int senderId, int receiverId, String content) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("conversationId", conversationId);
        p.addProperty("senderId", senderId);
        p.addProperty("receiverId", receiverId);
        if (content != null) p.addProperty("content", content);
        p.addProperty("token", ctx.getToken());
        Response r = send("message.send", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public void deleteMessage(int messageId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("messageId", messageId);
        p.addProperty("token", ctx.getToken());
        Response r = send("message.delete", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public void markMessagesRead(int conversationId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("conversationId", conversationId);
        p.addProperty("token", ctx.getToken());
        Response r = send("message.markRead", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public long getUnreadCount() throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("token", ctx.getToken());
        Response r = send("message.countUnread", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return ((Number) r.getData()).longValue();
    }

    // ==================== Auth ====================

    public void serverLogout() throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("token", ctx.getToken());
        send("auth.logout", p);
    }

    // ==================== User Profile ====================

    public Map<String, Object> getUserById(int userId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("userId", userId);
        Response r = send("user.findById", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), MAP_TYPE);
    }

    public void updateProfile(int userId, String phone, String email, String avatar) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("userId", userId);
        if (phone != null) p.addProperty("phone", phone);
        if (email != null) p.addProperty("email", email);
        if (avatar != null) p.addProperty("avatar", avatar);
        p.addProperty("token", ctx.getToken());
        Response r = send("user.updateProfile", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    /**
     * 根据用户ID解析用户名。
     * 先查缓存，缓存未命中则调用服务端查询（带限流保护）。
     */
    public String resolveUsername(int userId) throws Exception {
        if (usernameCache.containsKey(userId)) return usernameCache.get(userId);
        Map<String, Object> user = getUserById(userId);
        String name = (String) user.get("username");
        if (name != null) usernameCache.put(userId, name);
        return name != null ? name : "用户" + userId;
    }

    // ==================== Admin: User ====================

    public List<Map<String, Object>> listUsers(int page) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("page", page);
        p.addProperty("pageSize", 20);
        p.addProperty("token", ctx.getToken());
        Response r = send("user.list", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), LIST_MAP_TYPE);
    }

    public void updateUserStatus(int userId, int status) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("userId", userId);
        p.addProperty("status", status);
        p.addProperty("token", ctx.getToken());
        Response r = send("user.updateStatus", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public Map<String, Object> findByUsername(String username) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("username", username);
        Response r = send("user.findByUsername", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
        return GSON.fromJson(toJsonElement(r.getData()), MAP_TYPE);
    }

    // ==================== Admin: Category ====================

    public void createCategory(String name, int parentId, int sortOrder) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("name", name);
        p.addProperty("parentId", parentId);
        p.addProperty("sortOrder", sortOrder);
        p.addProperty("token", ctx.getToken());
        Response r = send("category.create", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public void updateCategory(int categoryId, String name, int sortOrder) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("categoryId", categoryId);
        p.addProperty("name", name);
        p.addProperty("sortOrder", sortOrder);
        p.addProperty("token", ctx.getToken());
        Response r = send("category.update", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }

    public void deleteCategory(int categoryId) throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("categoryId", categoryId);
        p.addProperty("token", ctx.getToken());
        Response r = send("category.delete", p);
        if (!r.isSuccess()) throw new RuntimeException(r.getMessage());
    }
}
