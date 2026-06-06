package com.example.entity;

import com.example.annotation.Column;
import com.example.annotation.Id;
import com.example.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类 — 映射到 {@code orders} 表。
 *
 * <h3>订单状态机</h3>
 * <pre>
 *  待支付(1) ──付款──▶ 待发货(2) ──发货──▶ 待收货(3) ──确认收货──▶ 已完成(4)
 *     │                    │                    │
 *     └────── 取消 ────────┴────── 取消 ────────┘
 *                       ↓
 *                    已取消(5)
 * </pre>
 *
 * <h3>字段说明</h3>
 * <table>
 *   <tr><th>字段</th><th>数据库列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>orderId</td><td>order_id</td><td>INT PK</td><td>订单ID，自增主键</td></tr>
 *   <tr><td>orderNo</td><td>order_no</td><td>VARCHAR(32) UNIQUE</td><td>订单号，UUID生成，唯一</td></tr>
 *   <tr><td>buyerId</td><td>buyer_id</td><td>INT FK</td><td>买家用户ID</td></tr>
 *   <tr><td>sellerId</td><td>seller_id</td><td>INT FK</td><td>卖家用户ID（冗余，加速查询）</td></tr>
 *   <tr><td>receiverName</td><td>receiver_name</td><td>VARCHAR(50)</td><td>收货人姓名</td></tr>
 *   <tr><td>receiverPhone</td><td>receiver_phone</td><td>VARCHAR(20)</td><td>收货人电话</td></tr>
 *   <tr><td>address</td><td>address</td><td>VARCHAR(500)</td><td>收货地址</td></tr>
 *   <tr><td>totalAmount</td><td>total_amount</td><td>DECIMAL(10,2)</td><td>订单总金额</td></tr>
 *   <tr><td>buyerNote</td><td>buyer_note</td><td>VARCHAR(200)</td><td>买家备注，可选</td></tr>
 *   <tr><td>logisticsCompany</td><td>logistics_company</td><td>VARCHAR(50)</td><td>物流公司名称</td></tr>
 *   <tr><td>logisticsNo</td><td>logistics_no</td><td>VARCHAR(50)</td><td>物流单号</td></tr>
 *   <tr><td>status</td><td>status</td><td>TINYINT</td><td>订单状态（见状态机）</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>DATETIME</td><td>下单时间</td></tr>
 *   <tr><td>payTime</td><td>pay_time</td><td>DATETIME</td><td>付款时间</td></tr>
 *   <tr><td>shipTime</td><td>ship_time</td><td>DATETIME</td><td>发货时间</td></tr>
 *   <tr><td>completeTime</td><td>complete_time</td><td>DATETIME</td><td>完成时间</td></tr>
 *   <tr><td>cancelReason</td><td>cancel_reason</td><td>VARCHAR(500)</td><td>取消原因</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>DATETIME</td><td>更新时间，自动更新</td></tr>
 * </table>
 *
 * <h3>状态常量（在 OrderService 中定义）</h3>
 * <pre>{@code
 * 1 = STATUS_PENDING_PAY    待支付（买家已下单，等待付款）
 * 2 = STATUS_PENDING_SHIP   待发货（买家已付款，等待卖家发货）
 * 3 = STATUS_PENDING_RECEIVE 待收货（卖家已发货，等待买家确认）
 * 4 = STATUS_COMPLETED      已完成（买家已确认收货）
 * 5 = STATUS_CANCELLED      已取消（买家或系统取消）
 * }</pre>
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see OrderItem
 * @see com.example.service.OrderService
 * @since 1.0
 */
@Table("orders")
public class Order {

    @Id
    @Column("order_id")
    private Integer orderId;

    /** 订单号，UUID 生成，全局唯一 */
    @Column("order_no")
    private String orderNo;

    /** 买家用户ID */
    @Column("buyer_id")
    private Integer buyerId;

    /** 卖家用户ID（冗余存储，加速卖家侧查询） */
    @Column("seller_id")
    private Integer sellerId;

    /** 收货人姓名 */
    @Column("receiver_name")
    private String receiverName;

    /** 收货人联系电话 */
    @Column("receiver_phone")
    private String receiverPhone;

    /** 收货详细地址 */
    private String address;

    /** 订单总金额（由 OrderService 计算 = Σ(price × quantity)） */
    @Column("total_amount")
    private BigDecimal totalAmount;

    /** 买家下单备注，可选 */
    @Column("buyer_note")
    private String buyerNote;

    /** 物流公司名称（卖家发货时填写） */
    @Column("logistics_company")
    private String logisticsCompany;

    /** 物流单号（卖家发货时填写） */
    @Column("logistics_no")
    private String logisticsNo;

    /** 订单状态：1=待支付, 2=待发货, 3=待收货, 4=已完成, 5=已取消 */
    private Integer status;

    /** 下单时间 */
    @Column("create_time")
    private LocalDateTime createTime;

    /** 付款时间 */
    @Column("pay_time")
    private LocalDateTime payTime;

    /** 卖家发货时间 */
    @Column("ship_time")
    private LocalDateTime shipTime;

    /** 买家确认收货/订单完成时间 */
    @Column("complete_time")
    private LocalDateTime completeTime;

    /** 取消原因（取消时填写） */
    @Column("cancel_reason")
    private String cancelReason;

    /** 最后更新时间，自动更新 */
    @Column("update_time")
    private LocalDateTime updateTime;

    /** 无参构造器（JdbcUtils 反射实例化需要） */
    public Order() {}

    // ==================== Getter / Setter ====================

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Integer getBuyerId() { return buyerId; }
    public void setBuyerId(Integer buyerId) { this.buyerId = buyerId; }
    public Integer getSellerId() { return sellerId; }
    public void setSellerId(Integer sellerId) { this.sellerId = sellerId; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getBuyerNote() { return buyerNote; }
    public void setBuyerNote(String buyerNote) { this.buyerNote = buyerNote; }
    public String getLogisticsCompany() { return logisticsCompany; }
    public void setLogisticsCompany(String logisticsCompany) { this.logisticsCompany = logisticsCompany; }
    public String getLogisticsNo() { return logisticsNo; }
    public void setLogisticsNo(String logisticsNo) { this.logisticsNo = logisticsNo; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }
    public LocalDateTime getShipTime() { return shipTime; }
    public void setShipTime(LocalDateTime shipTime) { this.shipTime = shipTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
