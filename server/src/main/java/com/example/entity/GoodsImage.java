package com.example.entity;

import com.example.annotation.Column;
import com.example.annotation.Id;
import com.example.annotation.Table;

/**
 * 商品图片实体类 — 映射到 {@code goods_images} 表。
 *
 * <h3>设计说明</h3>
 * 一个商品可以有多张图片，通过 {@code goods_id} 外键关联。
 * 图片按 {@code sort_order} 排序显示，数字越小越靠前。
 *
 * <h3>字段说明</h3>
 * <table>
 *   <tr><th>字段</th><th>数据库列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>imageId</td><td>image_id</td><td>INT PK</td><td>图片ID，自增主键</td></tr>
 *   <tr><td>goodsId</td><td>goods_id</td><td>INT FK</td><td>所属商品ID，外键关联 goods</td></tr>
 *   <tr><td>imageUrl</td><td>image_url</td><td>VARCHAR(500)</td><td>图片存储路径（如 /uploads/xxx.jpg）</td></tr>
 *   <tr><td>sortOrder</td><td>sort_order</td><td>INT</td><td>排序序号，越小越靠前，默认0</td></tr>
 * </table>
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see Goods
 * @see com.example.service.GoodsImageService
 * @since 1.0
 */
@Table("goods_images")
public class GoodsImage {

    /** 图片ID，自增主键（对应 image_id） */
    @Id
    @Column("image_id")
    private Integer imageId;

    /** 所属商品ID，外键关联 goods 表（对应 goods_id） */
    @Column("goods_id")
    private Integer goodsId;

    /** 图片存储路径（对应 image_url） */
    @Column("image_url")
    private String imageUrl;

    /** 排序序号，数字越小越靠前（对应 sort_order） */
    @Column("sort_order")
    private Integer sortOrder;

    /** 无参构造器（JdbcUtils 反射实例化需要） */
    public GoodsImage() {}

    // ==================== Getter / Setter ====================

    public Integer getImageId() { return imageId; }
    public void setImageId(Integer imageId) { this.imageId = imageId; }
    public Integer getGoodsId() { return goodsId; }
    public void setGoodsId(Integer goodsId) { this.goodsId = goodsId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
