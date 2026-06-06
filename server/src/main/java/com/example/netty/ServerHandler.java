package com.example.netty;

import com.example.entity.Goods;
import com.example.entity.Order;
import com.example.entity.OrderItem;
import com.example.service.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 服务端核心处理器 — 请求解析、认证拦截、路由分发、响应写回。
 *
 * requireAuth 会将 Token 对应的 userId 注入 params 的 _userId 字段，
 * 业务方法应使用此字段而非客户端传入的 userId 参数，防止越权。
 */
public class ServerHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>) (src, type, ctx) ->
                            new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonDeserializer<LocalDateTime>) (json, type, ctx) ->
                            LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .create();

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final UserService userService = new UserService();
    private final GoodsService goodsService = new GoodsService();
    private final OrderService orderService = new OrderService();
    private final CategoryService categoryService = new CategoryService();
    private final GoodsImageService goodsImageService = new GoodsImageService();
    private final FavoriteService favoriteService = new FavoriteService();
    private final AuthService authService = new AuthService();
    private final ConversationService conversationService = new ConversationService();
    private final MessageService messageService = new MessageService();
    private final FileService fileService = new FileService();

    private final Map<String, Function<JsonObject, Response>> dispatchMap = new HashMap<>();

    public ServerHandler() {
        initDispatch();
    }

    private void initDispatch() {

        // ===== UserService =====
        dispatchMap.put("user.register", p -> {
            String username = ParamValidator.requireStr(p, "username");
            String password = ParamValidator.requireStr(p, "password");
            return Response.ok(userService.register(username, password,
                    getStr(p, "phone"), getStr(p, "email")));
        });

        dispatchMap.put("user.login", p -> {
            String username = ParamValidator.requireStr(p, "username");
            String password = ParamValidator.requireStr(p, "password");
            var user = userService.login(username, password);
            String token = authService.login(user.getUserId());
            Map<String, Object> result = new HashMap<>();
            result.put("user", user);
            result.put("token", token);
            return Response.ok(result);
        });

        dispatchMap.put("user.findByUsername", p ->
                Response.ok(userService.findByUsername(
                        ParamValidator.requireStr(p, "username"))));

        dispatchMap.put("user.findById", p ->
                Response.ok(userService.findById(
                        ParamValidator.requirePositiveInt(p, "userId"))));

        dispatchMap.put("user.updateStatus", requireAuth(p -> {
            int userId = ParamValidator.requirePositiveInt(p, "userId");
            int status = ParamValidator.requireInt(p, "status");
            userService.updateStatus(userId, status);
            return Response.ok();
        }));

        dispatchMap.put("user.list", requireAuth(p ->
                Response.ok(userService.listUsers(
                        getInt(p, "page", 1), getInt(p, "pageSize", 10)))));

        dispatchMap.put("user.updateProfile", requireAuth(p -> {
            int userId = getAuthUserId(p);  // 从 _userId 取，防止越权
            userService.updateProfile(userId, getStr(p, "phone"),
                    getStr(p, "email"), getStr(p, "avatar"));
            return Response.ok();
        }));

        dispatchMap.put("user.count", p ->
                Response.ok(userService.countUsers()));

        // ===== GoodsService =====
        dispatchMap.put("goods.publish", requireAuth(p -> {
            int sellerId = getAuthUserId(p);
            int categoryId = ParamValidator.requirePositiveInt(p, "categoryId");
            String title = ParamValidator.requireStr(p, "title");
            BigDecimal price = ParamValidator.requireBigDecimal(p, "price");
            return Response.ok(goodsService.publish(sellerId, categoryId, title,
                    getStr(p, "description"), price,
                    p.has("originalPrice") ? new BigDecimal(p.get("originalPrice").getAsString()) : null));
        }));

        dispatchMap.put("goods.findById", p ->
                Response.ok(goodsService.findById(
                        ParamValidator.requirePositiveInt(p, "goodsId"))));

        dispatchMap.put("goods.findByCategory", p ->
                Response.ok(goodsService.findByCategory(
                        ParamValidator.requirePositiveInt(p, "categoryId"),
                        getInt(p, "page", 1), getInt(p, "pageSize", 10))));

        dispatchMap.put("goods.search", p ->
                Response.ok(goodsService.search(
                        ParamValidator.requireStr(p, "keyword"),
                        getInt(p, "page", 1), getInt(p, "pageSize", 10))));

        dispatchMap.put("goods.offShelf", requireAuth(p -> {
            int sellerId = getAuthUserId(p);
            int goodsId = ParamValidator.requirePositiveInt(p, "goodsId");
            goodsService.offShelf(goodsId, sellerId);
            return Response.ok();
        }));

        dispatchMap.put("goods.listBySeller", requireAuth(p ->
                Response.ok(goodsService.listBySeller(
                        getAuthUserId(p),
                        getInt(p, "page", 1), getInt(p, "pageSize", 10)))));

        dispatchMap.put("goods.update", requireAuth(p -> {
            int goodsId = ParamValidator.requirePositiveInt(p, "goodsId");
            // 校验卖家身份 — 只能修改自己的商品
            Goods goods = goodsService.findById(goodsId);
            if (goods == null) throw new RuntimeException("商品不存在");
            if (goods.getSellerId() != getAuthUserId(p)) throw new RuntimeException("无权修改此商品");
            goodsService.updateGoods(goodsId,
                    getStr(p, "title"), getStr(p, "description"),
                    p.has("price") ? new BigDecimal(p.get("price").getAsString()) : null,
                    p.has("status") ? p.get("status").getAsInt() : null);
            return Response.ok();
        }));

        // ===== OrderService =====
        dispatchMap.put("order.findById", requireAuth(p -> {
            int userId = getAuthUserId(p);
            int orderId = ParamValidator.requirePositiveInt(p, "orderId");
            Order order = orderService.findById(orderId);
            if (order == null) throw new RuntimeException("订单不存在");
            // 仅买家和卖家可查看
            if (order.getBuyerId() != userId && order.getSellerId() != userId) {
                throw new RuntimeException("无权查看此订单");
            }
            return Response.ok(order);
        }));

        dispatchMap.put("order.findByOrderNo", requireAuth(p ->
                Response.ok(orderService.findByOrderNo(
                        ParamValidator.requireStr(p, "orderNo")))));

        dispatchMap.put("order.findItems", requireAuth(p ->
                Response.ok(orderService.findItems(
                        ParamValidator.requirePositiveInt(p, "orderId")))));

        dispatchMap.put("order.listByBuyer", requireAuth(p ->
                Response.ok(orderService.listByBuyer(
                        getAuthUserId(p),
                        getInt(p, "page", 1), getInt(p, "pageSize", 10)))));

        dispatchMap.put("order.listBySeller", requireAuth(p ->
                Response.ok(orderService.listBySeller(
                        getAuthUserId(p),
                        getInt(p, "page", 1), getInt(p, "pageSize", 10)))));

        dispatchMap.put("order.pay", requireAuth(p -> {
            int buyerId = getAuthUserId(p);
            orderService.pay(ParamValidator.requirePositiveInt(p, "orderId"), buyerId);
            return Response.ok();
        }));

        dispatchMap.put("order.ship", requireAuth(p -> {
            int sellerId = getAuthUserId(p);
            orderService.ship(
                    ParamValidator.requirePositiveInt(p, "orderId"),
                    sellerId,
                    ParamValidator.requireStr(p, "logisticsCompany"),
                    ParamValidator.requireStr(p, "logisticsNo"));
            return Response.ok();
        }));

        dispatchMap.put("order.complete", requireAuth(p -> {
            int buyerId = getAuthUserId(p);
            orderService.complete(ParamValidator.requirePositiveInt(p, "orderId"), buyerId);
            return Response.ok();
        }));

        dispatchMap.put("order.cancel", requireAuth(p -> {
            int buyerId = getAuthUserId(p);
            orderService.cancel(ParamValidator.requirePositiveInt(p, "orderId"),
                    buyerId, getStr(p, "reason"));
            return Response.ok();
        }));

        dispatchMap.put("order.create", requireAuth(p -> {
            int buyerId = getAuthUserId(p);
            String receiverName = ParamValidator.requireStr(p, "receiverName");
            String receiverPhone = ParamValidator.requireStr(p, "receiverPhone");
            String address = ParamValidator.requireStr(p, "address");
            List<OrderService.OrderItemRequest> items = GSON.fromJson(
                    p.get("items").getAsJsonArray(),
                    new TypeToken<List<OrderService.OrderItemRequest>>() {}.getType());
            return Response.ok(orderService.createOrder(buyerId,
                    receiverName, receiverPhone, address, getStr(p, "buyerNote"), items));
        }));

        // ===== CategoryService =====
        dispatchMap.put("category.findAll", p ->
                Response.ok(categoryService.findAll()));

        dispatchMap.put("category.findById", p ->
                Response.ok(categoryService.findById(
                        ParamValidator.requirePositiveInt(p, "categoryId"))));

        dispatchMap.put("category.findByParent", p ->
                Response.ok(categoryService.findByParent(
                        ParamValidator.requirePositiveInt(p, "parentId"))));

        dispatchMap.put("category.create", requireAuth(p ->
                Response.ok(categoryService.create(
                        ParamValidator.requireStr(p, "name"),
                        getInt(p, "parentId", 0), getInt(p, "sortOrder", 0)))));

        dispatchMap.put("category.update", requireAuth(p -> {
            categoryService.update(
                    ParamValidator.requirePositiveInt(p, "categoryId"),
                    ParamValidator.requireStr(p, "name"),
                    getInt(p, "sortOrder", 0));
            return Response.ok();
        }));

        dispatchMap.put("category.delete", requireAuth(p -> {
            categoryService.delete(ParamValidator.requirePositiveInt(p, "categoryId"));
            return Response.ok();
        }));

        // ===== GoodsImageService =====
        dispatchMap.put("goodsImage.findByGoods", p ->
                Response.ok(goodsImageService.findByGoods(
                        ParamValidator.requirePositiveInt(p, "goodsId"))));

        dispatchMap.put("goodsImage.add", requireAuth(p ->
                Response.ok(goodsImageService.add(
                        ParamValidator.requirePositiveInt(p, "goodsId"),
                        ParamValidator.requireStr(p, "imageUrl"),
                        getInt(p, "sortOrder", 0)))));

        dispatchMap.put("goodsImage.delete", requireAuth(p -> {
            goodsImageService.delete(ParamValidator.requirePositiveInt(p, "imageId"));
            return Response.ok();
        }));

        // ===== FileService =====
        dispatchMap.put("file.upload", requireAuth(p ->
                Response.ok(fileService.upload(
                        ParamValidator.requireStr(p, "base64"),
                        getStr(p, "fileName")))));

        dispatchMap.put("file.download", p ->
                Response.ok(fileService.download(
                        ParamValidator.requireStr(p, "path"))));

        // ===== FavoriteService =====
        dispatchMap.put("favorite.add", requireAuth(p ->
                Response.ok(favoriteService.add(
                        getAuthUserId(p),
                        ParamValidator.requirePositiveInt(p, "goodsId")))));

        dispatchMap.put("favorite.remove", requireAuth(p -> {
            favoriteService.remove(getAuthUserId(p),
                    ParamValidator.requirePositiveInt(p, "goodsId"));
            return Response.ok();
        }));

        dispatchMap.put("favorite.listByUser", requireAuth(p ->
                Response.ok(favoriteService.listByUser(
                        getAuthUserId(p),
                        getInt(p, "page", 1), getInt(p, "pageSize", 10)))));

        dispatchMap.put("favorite.check", requireAuth(p ->
                Response.ok(favoriteService.check(
                        getAuthUserId(p),
                        ParamValidator.requirePositiveInt(p, "goodsId")))));

        dispatchMap.put("favorite.countByGoods", p ->
                Response.ok(favoriteService.countByGoods(
                        ParamValidator.requirePositiveInt(p, "goodsId"))));

        // ===== AuthService =====
        dispatchMap.put("auth.logout", p -> {
            authService.logout(ParamValidator.requireStr(p, "token"));
            return Response.ok();
        });

        // ===== ConversationService =====
        dispatchMap.put("conversation.createOrGet", requireAuth(p ->
                Response.ok(conversationService.createOrGet(
                        getAuthUserId(p),
                        ParamValidator.requirePositiveInt(p, "userId2")))));

        dispatchMap.put("conversation.listByUser", requireAuth(p ->
                Response.ok(conversationService.listByUser(
                        getAuthUserId(p),
                        getInt(p, "page", 1), getInt(p, "pageSize", 10)))));

        dispatchMap.put("conversation.markRead", requireAuth(p -> {
            conversationService.markRead(
                    ParamValidator.requirePositiveInt(p, "conversationId"),
                    getAuthUserId(p));
            return Response.ok();
        }));

        // ===== MessageService =====
        dispatchMap.put("message.send", requireAuth(p ->
                Response.ok(messageService.send(
                        ParamValidator.requirePositiveInt(p, "conversationId"),
                        getAuthUserId(p),  // senderId 从 Token 取
                        ParamValidator.requirePositiveInt(p, "receiverId"),
                        getStr(p, "content"),
                        getStr(p, "imagePath")))));

        dispatchMap.put("message.listByConversation", requireAuth(p ->
                Response.ok(messageService.listByConversation(
                        ParamValidator.requirePositiveInt(p, "conversationId"),
                        getInt(p, "page", 1), getInt(p, "pageSize", 20)))));

        dispatchMap.put("message.markRead", requireAuth(p -> {
            messageService.markRead(
                    ParamValidator.requirePositiveInt(p, "conversationId"),
                    getAuthUserId(p));
            return Response.ok();
        }));

        dispatchMap.put("message.countUnread", requireAuth(p ->
                Response.ok(messageService.countUnread(getAuthUserId(p)))));

        dispatchMap.put("message.delete", requireAuth(p -> {
            int userId = getAuthUserId(p);
            messageService.delete(ParamValidator.requirePositiveInt(p, "messageId"), userId);
            return Response.ok();
        }));
    }

    /**
     * 认证中间件：校验 Token → 注入 _userId → 执行业务。
     *
     * Token 校验通过后，将 userId 写入 params._userId，
     * 业务方法通过 getAuthUserId(p) 获取，不再信任客户端传入的 userId。
     */
    private Function<JsonObject, Response> requireAuth(Function<JsonObject, Response> fn) {
        return params -> {
            String token = getStr(params, "token");
            if (token == null) {
                return Response.fail("未登录或登录已过期");
            }
            Integer userId = authService.validate(token);
            if (userId == null) {
                return Response.fail("未登录或登录已过期");
            }
            // 注入认证后的 userId，业务层通过 getAuthUserId() 获取
            params.addProperty("_userId", userId);
            return fn.apply(params);
        };
    }

    /** 从 requireAuth 注入的 _userId 中获取当前登录用户 ID */
    private static int getAuthUserId(JsonObject p) {
        return p.get("_userId").getAsInt();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        Request request;
        int reqId = -1;

        try {
            request = GSON.fromJson(msg, Request.class);
            reqId = request.get_reqId();
        } catch (Exception e) {
            log.warn("JSON parse error: {}", e.getMessage());
            write(ctx, Response.fail("JSON parse error: " + e.getMessage()), -1);
            return;
        }

        Function<JsonObject, Response> fn = dispatchMap.get(request.getAction());
        if (fn == null) {
            write(ctx, Response.fail("未知 action: " + request.getAction()), reqId);
            return;
        }

        try {
            JsonObject params = request.getParams() != null ? request.getParams() : new JsonObject();
            Response response = fn.apply(params);
            write(ctx, response, reqId);
        } catch (Exception e) {
            String err = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error("Dispatch error: action={} error={}", request.getAction(), err, e);
            write(ctx, Response.fail(err), reqId);
        }
    }

    private static void write(ChannelHandlerContext ctx, Response resp, int reqId) {
        resp._reqId = reqId;
        ctx.writeAndFlush(GSON.toJson(resp) + "\n");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception", cause);
        ctx.close();
    }

    // ===== 安全的参数提取方法 =====

    /** 获取可选字符串参数，安全处理 JsonNull */
    private static String getStr(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        JsonElement e = obj.get(key);
        return e.isJsonNull() ? null : e.getAsString();
    }

    /** 获取可选整数参数，安全处理 JsonNull 和格式错误 */
    private static int getInt(JsonObject obj, String key, int defaultValue) {
        if (!obj.has(key)) return defaultValue;
        JsonElement e = obj.get(key);
        if (e.isJsonNull()) return defaultValue;
        try {
            return e.getAsInt();
        } catch (Exception ex) {
            return defaultValue;
        }
    }
}
