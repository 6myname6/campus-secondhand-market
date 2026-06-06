package com.example.entity;

import com.example.annotation.Column;
import com.example.annotation.Id;
import com.example.annotation.Table;

import java.time.LocalDateTime;

/**
 * 收藏夹实体类 — 映射到 {@code favorites} 表。
 *
 * <h3>设计说明</h3>
 * 记录用户对商品的收藏关系。每个用户对同一商品只能收藏一次（数据库 UNIQUE 约束保证）。
 *
 * <h3>字段说明</h3>
 * <table>
 *   <tr><th>字段</th><th>数据库列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>favoriteId</td><td>favorite_id</td><td>INT PK</td><td>收藏ID，自增主键</td></tr>
 *   <tr><td>userId</td><td>user_id</td><td>INT FK</td><td>用户ID，外键关联 users</td></tr>
 *   <tr><td>goodsId</td><td>goods_id</td><td>INT FK</td><td>商品ID，外键关联 goods</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>DATETIME</td><td>收藏时间，自动填充</td></tr>
 * </table>
 *
 * <h3>数据库约束</h3>
 * {@code UNIQUE KEY uk_user_goods (user_id, goods_id)} — 同一用户不可重复收藏同一商品。
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see com.example.service.FavoriteService
 * @since 1.0
 */
@Table("favorites")
public class Favorite {

    /** 收藏ID，自增主键（对应 favorite_id） */
    @Id
    @Column("favorite_id")
    private Integer favoriteId;

    /** 用户ID（对应 user_id） */
    @Column("user_id")
    private Integer userId;

    /** 商品ID（对应 goods_id） */
    @Column("goods_id")
    private Integer goodsId;

    /** 收藏时间（对应 create_time） */
    @Column("create_time")
    private LocalDateTime createTime;

    /** 无参构造器（JdbcUtils 反射实例化需要） */
    public Favorite() {}

    // ==================== Getter / Setter ====================

    public Integer getFavoriteId() { return favoriteId; }
    public void setFavoriteId(Integer favoriteId) { this.favoriteId = favoriteId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Integer getGoodsId() { return goodsId; }
    public void setGoodsId(Integer goodsId) { this.goodsId = goodsId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
