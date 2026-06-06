package com.example.service;

import com.example.JdbcUtils;
import com.example.entity.Goods;
import com.example.entity.Order;
import com.example.entity.OrderItem;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

/**
 * 订单服务 — 完整的订单生命周期管理。
 *
 * 状态常量 (避免魔术数字):
 *   STATUS_PENDING_PAY = 1 (待支付)
 *   STATUS_PENDING_SHIP = 2 (待发货)
 *   STATUS_PENDING_RECEIVE = 3 (待收货)
 *   STATUS_COMPLETED = 4 (已完成)
 *   STATUS_CANCELLED = 5 (已取消)
 *
 * 商品状态: STATUS_ON_SALE = 1 (在售), STATUS_SOLD = 2 (已售), STATUS_OFF_SHELF = 3 (下架)
 */
public class OrderService extends BaseService {

    // 订单状态常量
    public static final int STATUS_PENDING_PAY = 1;
    public static final int STATUS_PENDING_SHIP = 2;
    public static final int STATUS_PENDING_RECEIVE = 3;
    public static final int STATUS_COMPLETED = 4;
    public static final int STATUS_CANCELLED = 5;

    // 商品状态常量
    private static final int GOODS_ON_SALE = 1;
    private static final int GOODS_SOLD = 2;

    /**
     * 创建订单（事务：校验商品 → 插入订单 → 插入明细 → 锁定商品状态）。
     *
     * 防竞态：先 SELECT ... FOR UPDATE 锁定商品行，再更新状态。
     * 防自买：校验 buyerId != sellerId。
     */
    public int createOrder(int buyerId, String receiverName, String receiverPhone,
                            String address, String buyerNote, List<OrderItemRequest> items) {
        return execTx(conn -> {
            // 1. 校验商品存在、在售、非自买，并锁定；同时确定卖家
            int sellerId = 0;
            for (OrderItemRequest item : items) {
                Goods goods = JdbcUtils.queryOne(conn, Goods.class,
                        "SELECT * FROM goods WHERE goods_id = ? FOR UPDATE", item.getGoodsId());
                if (goods == null) {
                    throw new RuntimeException("商品 #" + item.getGoodsId() + " 不存在");
                }
                if (goods.getStatus() != GOODS_ON_SALE) {
                    throw new RuntimeException("商品 \"" + goods.getTitle() + "\" 已不在售");
                }
                if (goods.getSellerId() == buyerId) {
                    throw new RuntimeException("不能购买自己发布的商品");
                }
                if (sellerId == 0) sellerId = goods.getSellerId();
                // 使用实际价格
                item.setPrice(goods.getPrice());
            }

            // 2. 计算总金额
            BigDecimal total = BigDecimal.ZERO;
            for (OrderItemRequest item : items) {
                total = total.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }

            // 3. 插入订单（含 seller_id）
            String orderNo = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            int orderId = JdbcUtils.insertAndGetKey(conn,
                    "INSERT INTO orders (order_no, buyer_id, seller_id, receiver_name, receiver_phone, " +
                    "address, total_amount, buyer_note, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    orderNo, buyerId, sellerId, receiverName, receiverPhone, address, total, buyerNote,
                    STATUS_PENDING_PAY);

            // 4. 插入订单详情并更新商品状态
            for (OrderItemRequest item : items) {
                JdbcUtils.update(conn,
                        "INSERT INTO order_items (order_id, goods_id, price, quantity) VALUES (?, ?, ?, ?)",
                        orderId, item.getGoodsId(), item.getPrice(), item.getQuantity());
                int rows = JdbcUtils.update(conn,
                        "UPDATE goods SET status = ? WHERE goods_id = ? AND status = ?",
                        GOODS_SOLD, item.getGoodsId(), GOODS_ON_SALE);
                if (rows == 0) {
                    // 并发冲突：商品已被其他请求锁定/购买 — 回滚事务
                    throw new RuntimeException("商品已售出，请刷新重试");
                }
            }

            return orderId;
        });
    }

    /** 根据ID查订单 */
    public Order findById(int orderId) {
        return execTx(conn ->
                JdbcUtils.queryOne(conn, Order.class,
                        "SELECT * FROM orders WHERE order_id = ?", orderId));
    }

    /** 根据订单号查询 */
    public Order findByOrderNo(String orderNo) {
        return execTx(conn ->
                JdbcUtils.queryOne(conn, Order.class,
                        "SELECT * FROM orders WHERE order_no = ?", orderNo));
    }

    /** 查订单详情（订单商品列表） */
    public List<OrderItem> findItems(int orderId) {
        return execTx(conn ->
                JdbcUtils.queryList(conn, OrderItem.class,
                        "SELECT * FROM order_items WHERE order_id = ?", orderId));
    }

    /** 买家订单列表 */
    public List<Order> listByBuyer(int buyerId, int page, int pageSize) {
        return execTx(conn ->
                JdbcUtils.queryPage(conn, Order.class,
                        "SELECT * FROM orders WHERE buyer_id = ? ORDER BY create_time DESC",
                        page, pageSize, buyerId));
    }

    /** 卖家订单列表 — 直接通过 orders.seller_id 筛选 */
    public List<Order> listBySeller(int sellerId, int page, int pageSize) {
        return execTx(conn ->
                JdbcUtils.queryPage(conn, Order.class,
                        "SELECT * FROM orders WHERE seller_id = ? ORDER BY create_time DESC",
                        page, pageSize, sellerId));
    }

    /** 支付（待支付 → 待发货） */
    public void pay(int orderId, int buyerId) {
        execTxVoid(conn -> {
            int rows = JdbcUtils.update(conn,
                    "UPDATE orders SET status = ?, pay_time = NOW() " +
                    "WHERE order_id = ? AND buyer_id = ? AND status = ?",
                    STATUS_PENDING_SHIP, orderId, buyerId, STATUS_PENDING_PAY);
            if (rows == 0) throw new RuntimeException("订单不存在或状态不允许支付");
        });
    }

    /** 发货（待发货 → 待收货），需校验卖家身份 */
    public void ship(int orderId, int sellerId, String logisticsCompany, String logisticsNo) {
        execTxVoid(conn -> {
            int rows = JdbcUtils.update(conn,
                    "UPDATE orders SET status = ?, logistics_company = ?, logistics_no = ?, ship_time = NOW() " +
                    "WHERE order_id = ? AND seller_id = ? AND status = ?",
                    STATUS_PENDING_RECEIVE, logisticsCompany, logisticsNo,
                    orderId, sellerId, STATUS_PENDING_SHIP);
            if (rows == 0) throw new RuntimeException("订单不存在或无权操作");
        });
    }

    /** 确认收货（待收货 → 已完成） */
    public void complete(int orderId, int buyerId) {
        execTxVoid(conn -> {
            int rows = JdbcUtils.update(conn,
                    "UPDATE orders SET status = ?, complete_time = NOW() " +
                    "WHERE order_id = ? AND buyer_id = ? AND status = ?",
                    STATUS_COMPLETED, orderId, buyerId, STATUS_PENDING_RECEIVE);
            if (rows == 0) throw new RuntimeException("订单不存在或状态不允许确认收货");
        });
    }

    /**
     * 取消订单 — 恢复商品状态。
     *
     * 取消时通过 order_items 找到关联商品，将商品状态从"已售(2)"恢复为"在售(1)"。
     */
    public void cancel(int orderId, int buyerId, String reason) {
        execTxVoid(conn -> {
            int rows = JdbcUtils.update(conn,
                    "UPDATE orders SET status = ?, cancel_reason = ? " +
                    "WHERE order_id = ? AND buyer_id = ? AND status IN (?, ?)",
                    STATUS_CANCELLED, reason, orderId, buyerId,
                    STATUS_PENDING_PAY, STATUS_PENDING_SHIP);
            if (rows == 0) throw new RuntimeException("订单不存在或状态不允许取消");

            // 恢复关联商品状态为在售
            JdbcUtils.update(conn,
                    "UPDATE goods SET status = ? WHERE goods_id IN " +
                    "(SELECT goods_id FROM order_items WHERE order_id = ?) AND status = ?",
                    GOODS_ON_SALE, orderId, GOODS_SOLD);
        });
    }

    /** 订单商品请求（内部类） */
    public static class OrderItemRequest {
        private int goodsId;
        private BigDecimal price;
        private int quantity;

        public OrderItemRequest() {}
        public OrderItemRequest(int goodsId, BigDecimal price, int quantity) {
            this.goodsId = goodsId; this.price = price; this.quantity = quantity;
        }
        public int getGoodsId() { return goodsId; }
        public void setGoodsId(int goodsId) { this.goodsId = goodsId; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
