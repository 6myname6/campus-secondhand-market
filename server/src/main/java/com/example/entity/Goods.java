package com.example.entity;

import com.example.annotation.Column;
import com.example.annotation.Id;
import com.example.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体类 — 映射到 {@code goods} 表。
 *
 * <h3>字段说明</h3>
 * <table>
 *   <tr><th>字段</th><th>数据库列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>goodsId</td><td>goods_id</td><td>INT PK</td><td>商品ID，自增主键</td></tr>
 *   <tr><td>sellerId</td><td>seller_id</td><td>INT FK</td><td>卖家用户ID，外键关联 users</td></tr>
 *   <tr><td>categoryId</td><td>category_id</td><td>INT FK</td><td>商品分类ID，外键关联 categories</td></tr>
 *   <tr><td>title</td><td>title</td><td>VARCHAR(200)</td><td>商品标题，必填</td></tr>
 *   <tr><td>description</td><td>description</td><td>TEXT</td><td>商品描述，可选</td></tr>
 *   <tr><td>price</td><td>price</td><td>DECIMAL(10,2)</td><td>售价，必填</td></tr>
 *   <tr><td>originalPrice</td><td>original_price</td><td>DECIMAL(10,2)</td><td>原价，可选</td></tr>
 *   <tr><td>status</td><td>status</td><td>TINYINT</td><td>状态：1=在售, 2=已售, 3=下架</td></tr>
 *   <tr><td>viewCount</td><td>view_count</td><td>INT</td><td>浏览量，默认0，每次查看+1</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>DATETIME</td><td>发布时间，自动填充</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>DATETIME</td><td>更新时间，自动更新</td></tr>
 * </table>
 *
 * <h3>状态常量（在各 Service 中使用）</h3>
 * <pre>{@code
 * public static final int STATUS_ON_SALE  = 1;  // 在售
 * public static final int STATUS_SOLD     = 2;  // 已售
 * public static final int STATUS_OFF      = 3;  // 下架
 * }</pre>
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see com.example.service.GoodsService
 * @since 1.0
 */
@Table("goods")
public class Goods {

    /** 商品ID，自增主键（对应 goods_id） */
    @Id
    @Column("goods_id")
    private Integer goodsId;

    /** 卖家用户ID，外键关联 users 表（对应 seller_id） */
    @Column("seller_id")
    private Integer sellerId;

    /** 商品分类ID，外键关联 categories 表（对应 category_id） */
    @Column("category_id")
    private Integer categoryId;

    /** 商品标题（自动匹配 title 列） */
    private String title;

    /** 商品描述，TEXT 类型（自动匹配 description 列） */
    private String description;

    /** 售价，使用 BigDecimal 保证金额精度（自动匹配 price 列） */
    private BigDecimal price;

    /** 商品原价/划线价，可选（对应 original_price） */
    @Column("original_price")
    private BigDecimal originalPrice;

    /** 商品状态：1=在售, 2=已售, 3=下架（自动匹配 status 列） */
    private Integer status;

    /** 浏览量计数，每次查看商品详情自动+1（对应 view_count） */
    @Column("view_count")
    private Integer viewCount;

    /** 发布时间（对应 create_time） */
    @Column("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间（对应 update_time） */
    @Column("update_time")
    private LocalDateTime updateTime;

    /** 无参构造器（JdbcUtils 反射实例化需要） */
    public Goods() {}

    // ==================== Getter / Setter ====================

    public Integer getGoodsId() { return goodsId; }
    public void setGoodsId(Integer goodsId) { this.goodsId = goodsId; }
    public Integer getSellerId() { return sellerId; }
    public void setSellerId(Integer sellerId) { this.sellerId = sellerId; }
    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
