package com.example.entity;

import com.example.annotation.Column;
import com.example.annotation.Id;
import com.example.annotation.Table;

import java.math.BigDecimal;

/**
 * 订单明细实体类 — 映射到 {@code order_items} 表。
 *
 * <h3>设计说明</h3>
 * 订单与商品之间是多对多关系（一个订单可含多个商品），
 * 通过 order_items 中间表关联。每条记录记录某商品在订单中的<b>快照价格</b>和数量。
 *
 * <h3>为什么存快照价格？</h3>
 * 商品价格会变动，订单明细中需要保存<b>下单时的价格</b>，
 * 而不是关联查询商品的当前价格，确保订单数据的历史准确性。
 *
 * <h3>字段说明</h3>
 * <table>
 *   <tr><th>字段</th><th>数据库列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>itemId</td><td>item_id</td><td>INT PK</td><td>明细ID，自增主键</td></tr>
 *   <tr><td>orderId</td><td>order_id</td><td>INT FK</td><td>所属订单ID，外键关联 orders</td></tr>
 *   <tr><td>goodsId</td><td>goods_id</td><td>INT FK</td><td>商品ID，外键关联 goods</td></tr>
 *   <tr><td>price</td><td>price</td><td>DECIMAL(10,2)</td><td>商品单价快照</td></tr>
 *   <tr><td>quantity</td><td>quantity</td><td>INT</td><td>购买数量，默认1</td></tr>
 * </table>
 *
 * <h3>数据库约束</h3>
 * {@code UNIQUE KEY uk_order_goods (order_id, goods_id)} — 同一订单同一商品不可重复。
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see Order
 * @since 1.0
 */
@Table("order_items")
public class OrderItem {

    /** 明细ID，自增主键（对应 item_id） */
    @Id
    @Column("item_id")
    private Integer itemId;

    /** 所属订单ID（对应 order_id） */
    @Column("order_id")
    private Integer orderId;

    /** 商品ID（对应 goods_id） */
    @Column("goods_id")
    private Integer goodsId;

    /** 商品下单时的单价快照（对应 price 列） */
    private BigDecimal price;

    /** 购买数量（对应 quantity 列） */
    private Integer quantity;

    /** 无参构造器（JdbcUtils 反射实例化需要） */
    public OrderItem() {}

    // ==================== Getter / Setter ====================

    public Integer getItemId() { return itemId; }
    public void setItemId(Integer itemId) { this.itemId = itemId; }
    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }
    public Integer getGoodsId() { return goodsId; }
    public void setGoodsId(Integer goodsId) { this.goodsId = goodsId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
