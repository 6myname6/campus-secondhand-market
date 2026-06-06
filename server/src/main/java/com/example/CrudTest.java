package com.example;

import com.example.entity.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

/**
 * CRUD 冒烟测试 — 覆盖全部 7 个实体类共 23 个测试用例。
 * 所有操作在一个事务中执行，测试完毕后回滚，不污染数据库。
 *
 * 使用显式异常抛出替代 Java assert，确保 JVM 无需 -ea 参数也能正确执行。
 */
public class CrudTest {

    private static int passed = 0;
    private static int total = 0;

    public static void main(String[] args) {
        System.out.println("========== CRUD 测试开始 ==========\n");

        try (Connection conn = JdbcUtils.getConnection()) {
            conn.setAutoCommit(false);

            try {
                testUserCrud(conn);
                testGoodsCrud(conn);
                testOrderCrud(conn);
                testOrderItemCrud(conn);
                testConversationCrud(conn);
                testMessageCrud(conn);
                testFavoriteCrud(conn);

                System.out.println("========== 全部测试通过 (" + passed + "/" + total + ") ==========");
            } finally {
                conn.rollback();
                System.out.println("(已回滚，数据库无变化)");
            }
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void check(boolean cond, String msg) {
        total++;
        if (cond) { passed++; System.out.println("  " + msg + " ✓"); }
        else throw new RuntimeException("断言失败: " + msg);
    }

    // ==================== User CRUD (5 个用例) ====================
    static void testUserCrud(Connection conn) {
        System.out.println("--- User CRUD (5 用例) ---");

        String sql = "INSERT INTO users (username, password, phone, email, role, status) VALUES (?, ?, ?, ?, ?, ?)";
        int userId = JdbcUtils.insertAndGetKey(conn, sql,
                "test_user", "encrypted_123", "13800000001", "test@example.com", "user", 1);
        check(userId > 0, "INSERT  userId=" + userId);

        User user = JdbcUtils.queryOne(conn, User.class, "SELECT * FROM users WHERE user_id = ?", userId);
        check(user != null && "test_user".equals(user.getUsername()), "SELECT  username=" + (user != null ? user.getUsername() : "null"));

        User safe = JdbcUtils.queryOne(conn, User.class, "SELECT * FROM users WHERE username = ? AND status = ?", "test_user", 1);
        check(safe != null && safe.getUserId().equals(userId), "SELECT  (PreparedStatement 防注入)");

        int updated = JdbcUtils.update(conn, "UPDATE users SET phone = ?, email = ? WHERE user_id = ?", "13900000002", "updated@example.com", userId);
        User updatedUser = JdbcUtils.queryOne(conn, User.class, "SELECT * FROM users WHERE user_id = ?", userId);
        check(updated == 1 && "13900000002".equals(updatedUser.getPhone()), "UPDATE  phone=" + (updatedUser != null ? updatedUser.getPhone() : "null"));

        int deleted = JdbcUtils.update(conn, "DELETE FROM users WHERE user_id = ?", userId);
        User deletedUser = JdbcUtils.queryOne(conn, User.class, "SELECT * FROM users WHERE user_id = ?", userId);
        check(deleted == 1 && deletedUser == null, "DELETE");

        System.out.println("  User CRUD: 5/5 通过\n");
    }

    // ==================== Goods CRUD (4 个用例) ====================
    static void testGoodsCrud(Connection conn) {
        System.out.println("--- Goods CRUD (4 用例) ---");

        String suffix = String.valueOf(System.currentTimeMillis() % 100000);
        int userId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO users (username, password) VALUES (?, ?)", "seller_" + suffix, "pwd");
        int categoryId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO categories (category_name) VALUES (?)", "crud_cat_" + suffix);

        Goods goods = new Goods();
        goods.setSellerId(userId); goods.setCategoryId(categoryId);
        goods.setTitle("iPhone 15 99新"); goods.setDescription("几乎全新");
        goods.setPrice(new BigDecimal("4999.00")); goods.setOriginalPrice(new BigDecimal("6999.00")); goods.setStatus(1);
        int goodsId = JdbcUtils.insert(conn, goods);
        check(goodsId > 0, "INSERT  goodsId=" + goodsId);

        Goods dbGoods = JdbcUtils.queryOne(conn, Goods.class, "SELECT * FROM goods WHERE goods_id = ?", goodsId);
        check(dbGoods != null && "iPhone 15 99新".equals(dbGoods.getTitle()), "SELECT  title=" + (dbGoods != null ? dbGoods.getTitle() : "null"));

        dbGoods.setPrice(new BigDecimal("4599.00"));
        int updated = JdbcUtils.updateById(conn, dbGoods);
        Goods updatedGoods = JdbcUtils.queryOne(conn, Goods.class, "SELECT * FROM goods WHERE goods_id = ?", goodsId);
        check(updated == 1 && new BigDecimal("4599.00").compareTo(updatedGoods.getPrice()) == 0, "UPDATE  price=" + (updatedGoods != null ? updatedGoods.getPrice() : "null"));

        int deleted = JdbcUtils.deleteById(conn, Goods.class, goodsId);
        Goods deletedGoods = JdbcUtils.queryOne(conn, Goods.class, "SELECT * FROM goods WHERE goods_id = ?", goodsId);
        check(deleted == 1 && deletedGoods == null, "DELETE");

        System.out.println("  Goods CRUD: 4/4 通过\n");
    }

    // ==================== Order CRUD (3 个用例) ====================
    static void testOrderCrud(Connection conn) {
        System.out.println("--- Order CRUD (3 用例) ---");

        String suffix = String.valueOf(System.currentTimeMillis() % 100000);
        int buyerId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO users (username, password) VALUES (?, ?)", "buyer_" + suffix, "pwd");
        int sellerId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO users (username, password) VALUES (?, ?)", "seller_" + suffix, "pwd");
        int catId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO categories (category_name) VALUES (?)", "order_cat_" + suffix);
        JdbcUtils.insertAndGetKey(conn, "INSERT INTO goods (seller_id, category_id, title, price, status) VALUES (?, ?, ?, ?, ?)",
                sellerId, catId, "Java编程思想", new BigDecimal("79.00"), 1);

        String orderNo = "ORD" + System.currentTimeMillis();
        int orderId = JdbcUtils.insertAndGetKey(conn,
                "INSERT INTO orders (order_no, buyer_id, receiver_name, receiver_phone, address, total_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                orderNo, buyerId, "张三", "13800000003", "北京市朝阳区xxx", new BigDecimal("79.00"), 1);
        check(orderId > 0, "INSERT  orderId=" + orderId);

        Order order = JdbcUtils.queryOne(conn, Order.class, "SELECT * FROM orders WHERE order_no = ?", orderNo);
        check(order != null && "张三".equals(order.getReceiverName()), "SELECT  receiver=" + (order != null ? order.getReceiverName() : "null"));

        int updated = JdbcUtils.update(conn, "UPDATE orders SET status = ?, logistics_company = ?, logistics_no = ? WHERE order_id = ?",
                2, "顺丰速运", "SF1234567890", orderId);
        Order updatedOrder = JdbcUtils.queryOne(conn, Order.class, "SELECT * FROM orders WHERE order_id = ?", orderId);
        check(updated == 1 && updatedOrder.getStatus() == 2, "UPDATE  status=" + (updatedOrder != null ? updatedOrder.getStatus() : -1));

        System.out.println("  Order CRUD: 3/3 通过\n");
    }

    // ==================== OrderItem CRUD (3 个用例) ====================
    static void testOrderItemCrud(Connection conn) {
        System.out.println("--- OrderItem CRUD (3 用例) ---");

        String suffix = String.valueOf(System.currentTimeMillis() % 100000);
        int buyerId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO users (username, password) VALUES (?, ?)", "oi_buyer_" + suffix, "pwd");
        int sellerId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO users (username, password) VALUES (?, ?)", "oi_seller_" + suffix, "pwd");
        int catId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO categories (category_name) VALUES (?)", "oi_cat_" + suffix);
        int goodsId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO goods (seller_id, category_id, title, price, status) VALUES (?, ?, ?, ?, ?)",
                sellerId, catId, "测试商品A", new BigDecimal("99.00"), 1);
        int goodsId2 = JdbcUtils.insertAndGetKey(conn, "INSERT INTO goods (seller_id, category_id, title, price, status) VALUES (?, ?, ?, ?, ?)",
                sellerId, catId, "测试商品B", new BigDecimal("50.00"), 1);

        String orderNo = "OI" + System.currentTimeMillis();
        int orderId = JdbcUtils.insertAndGetKey(conn,
                "INSERT INTO orders (order_no, buyer_id, receiver_name, receiver_phone, address, total_amount, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                orderNo, buyerId, "李四", "13900001111", "上海市浦东新区", new BigDecimal("149.00"), 1);

        int itemId1 = JdbcUtils.insertAndGetKey(conn,
                "INSERT INTO order_items (order_id, goods_id, price, quantity) VALUES (?, ?, ?, ?)", orderId, goodsId, new BigDecimal("99.00"), 1);
        check(itemId1 > 0, "INSERT  itemId1=" + itemId1);

        int itemId2 = JdbcUtils.insertAndGetKey(conn,
                "INSERT INTO order_items (order_id, goods_id, price, quantity) VALUES (?, ?, ?, ?)", orderId, goodsId2, new BigDecimal("50.00"), 2);
        check(itemId2 > 0, "INSERT  itemId2=" + itemId2);

        List<OrderItem> items = JdbcUtils.queryList(conn, OrderItem.class, "SELECT * FROM order_items WHERE order_id = ?", orderId);
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItem oi : items) sum = sum.add(oi.getPrice().multiply(new BigDecimal(oi.getQuantity())));
        check(items.size() == 2 && new BigDecimal("199.00").compareTo(sum) == 0, "SELECT  items=" + items.size() + " totalPrice=" + sum);

        System.out.println("  OrderItem CRUD: 3/3 通过\n");
    }

    // ==================== Conversation CRUD (2 个用例) ====================
    static void testConversationCrud(Connection conn) {
        System.out.println("--- Conversation CRUD (2 用例) ---");

        String suffix = String.valueOf(System.currentTimeMillis() % 100000);
        int user1 = JdbcUtils.insertAndGetKey(conn, "INSERT INTO users (username, password) VALUES (?, ?)", "conv_u1_" + suffix, "pwd");
        int user2 = JdbcUtils.insertAndGetKey(conn, "INSERT INTO users (username, password) VALUES (?, ?)", "conv_u2_" + suffix, "pwd");
        int smaller = Math.min(user1, user2), larger = Math.max(user1, user2);

        int convId = JdbcUtils.insertAndGetKey(conn,
                "INSERT INTO conversations (user1_id, user2_id, last_message, last_time) VALUES (?, ?, ?, NOW())", smaller, larger, "你好！");
        check(convId > 0, "INSERT  convId=" + convId);

        Conversation conv = JdbcUtils.queryOne(conn, Conversation.class, "SELECT * FROM conversations WHERE conversation_id = ?", convId);
        check(conv != null && conv.getUser1Id().equals(smaller) && "你好！".equals(conv.getLastMessage()),
                "SELECT  user1=" + (conv != null ? conv.getUser1Id() : "null") + " lastMsg=" + (conv != null ? conv.getLastMessage() : "null"));

        System.out.println("  Conversation CRUD: 2/2 通过\n");
    }

    // ==================== Message CRUD (3 个用例) ====================
    static void testMessageCrud(Connection conn) {
        System.out.println("--- Message CRUD (3 用例) ---");

        String suffix = String.valueOf(System.currentTimeMillis() % 100000);
        int user1 = JdbcUtils.insertAndGetKey(conn, "INSERT INTO users (username, password) VALUES (?, ?)", "msg_u1_" + suffix, "pwd");
        int user2 = JdbcUtils.insertAndGetKey(conn, "INSERT INTO users (username, password) VALUES (?, ?)", "msg_u2_" + suffix, "pwd");
        int smaller = Math.min(user1, user2), larger = Math.max(user1, user2);
        int convId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO conversations (user1_id, user2_id) VALUES (?, ?)", smaller, larger);

        int msgId = JdbcUtils.insertAndGetKey(conn,
                "INSERT INTO messages (conversation_id, sender_id, receiver_id, content) VALUES (?, ?, ?, ?)", convId, user1, user2, "你好！我对你的商品感兴趣");
        check(msgId > 0, "INSERT  msgId=" + msgId);

        List<Message> messages = JdbcUtils.queryList(conn, Message.class, "SELECT * FROM messages WHERE conversation_id = ? ORDER BY send_time", convId);
        check(messages.size() == 1 && "你好！我对你的商品感兴趣".equals(messages.get(0).getContent()), "SELECT  count=" + messages.size());

        JdbcUtils.update(conn, "UPDATE messages SET is_read = ? WHERE message_id = ?", 1, msgId);
        Message readMsg = JdbcUtils.queryOne(conn, Message.class, "SELECT * FROM messages WHERE message_id = ?", msgId);
        check(readMsg.getIsRead() == 1, "UPDATE  isRead=" + readMsg.getIsRead());

        System.out.println("  Message CRUD: 3/3 通过\n");
    }

    // ==================== Favorite CRUD (3 个用例) ====================
    static void testFavoriteCrud(Connection conn) {
        System.out.println("--- Favorite CRUD (3 用例) ---");

        String suffix = String.valueOf(System.currentTimeMillis() % 100000);
        int userId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO users (username, password) VALUES (?, ?)", "fav_u_" + suffix, "pwd");
        int catId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO categories (category_name) VALUES (?)", "fav_cat_" + suffix);
        int goodsId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO goods (seller_id, category_id, title, price, status) VALUES (?, ?, ?, ?, ?)",
                userId, catId, "收藏测试商品", new BigDecimal("100.00"), 1);

        int favId = JdbcUtils.insertAndGetKey(conn, "INSERT INTO favorites (user_id, goods_id) VALUES (?, ?)", userId, goodsId);
        check(favId > 0, "INSERT  favId=" + favId);

        Favorite fav = JdbcUtils.queryOne(conn, Favorite.class, "SELECT * FROM favorites WHERE user_id = ? AND goods_id = ?", userId, goodsId);
        check(fav != null && fav.getUserId().equals(userId), "SELECT  userId=" + (fav != null ? fav.getUserId() : "null"));

        JdbcUtils.update(conn, "DELETE FROM favorites WHERE favorite_id = ?", favId);
        Favorite deletedFav = JdbcUtils.queryOne(conn, Favorite.class, "SELECT * FROM favorites WHERE favorite_id = ?", favId);
        check(deletedFav == null, "DELETE");

        System.out.println("  Favorite CRUD: 3/3 通过\n");
    }
}
