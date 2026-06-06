package com.example.entity;

import com.example.annotation.Column;
import com.example.annotation.Id;
import com.example.annotation.Table;

/**
 * 商品分类实体类 — 映射到 {@code categories} 表。
 *
 * <h3>设计说明</h3>
 * 支持<b>两级分类</b>结构：
 * <ul>
 *   <li>{@code parent_id = 0} 表示顶级分类（如"电子产品"）</li>
 *   <li>{@code parent_id > 0} 表示子分类，值为父分类的 {@code category_id}</li>
 * </ul>
 * 分类按 {@code sort_order} 升序排列显示。
 *
 * <h3>字段说明</h3>
 * <table>
 *   <tr><th>字段</th><th>数据库列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>categoryId</td><td>category_id</td><td>INT PK</td><td>分类ID，自增主键</td></tr>
 *   <tr><td>parentId</td><td>parent_id</td><td>INT</td><td>父分类ID，0=顶级分类</td></tr>
 *   <tr><td>categoryName</td><td>category_name</td><td>VARCHAR(50) UNIQUE</td><td>分类名称，唯一</td></tr>
 *   <tr><td>sortOrder</td><td>sort_order</td><td>INT</td><td>排序序号，越小越靠前</td></tr>
 * </table>
 *
 * <h3>种子数据（init.sql 预设）</h3>
 * 预设 8 个顶级分类：电子产品、书籍教材、生活用品、运动户外、服饰鞋包、数码配件、文具办公、其他。
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see com.example.service.CategoryService
 * @since 1.0
 */
@Table("categories")
public class Category {

    /** 分类ID，自增主键（对应 category_id） */
    @Id
    @Column("category_id")
    private Integer categoryId;

    /** 父分类ID，0 表示顶级分类（对应 parent_id） */
    @Column("parent_id")
    private Integer parentId;

    /** 分类名称，唯一（对应 category_name） */
    @Column("category_name")
    private String categoryName;

    /** 排序序号（对应 sort_order） */
    @Column("sort_order")
    private Integer sortOrder;

    /** 无参构造器（JdbcUtils 反射实例化需要） */
    public Category() {}

    // ==================== Getter / Setter ====================

    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public Integer getParentId() { return parentId; }
    public void setParentId(Integer parentId) { this.parentId = parentId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
