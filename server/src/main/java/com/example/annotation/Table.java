package com.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义 ORM 注解：标记实体类对应的数据库表名。
 *
 * <h3>设计目的</h3>
 * 让实体类能够声明自己映射到哪张数据库表，避免硬编码表名。
 * JdbcUtils 通过读取此注解自动获取表名，实现通用 CRUD 操作。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Table("users")        // 映射到 users 表
 * public class User {
 *     // 字段定义...
 * }
 *
 * @Table("order_items")  // 映射到 order_items 表
 * public class OrderItem {
 *     // 字段定义...
 * }
 * }</pre>
 *
 * <h3>工作机制</h3>
 * <ol>
 *   <li>实体类标注 {@code @Table("表名")}</li>
 *   <li>JdbcUtils.getTableName() 在运行时通过反射读取此注解</li>
 *   <li>若未标注此注解，则自动将类名驼峰转蛇形作为表名（如 {@code OrderItem → order_item}）</li>
 * </ol>
 *
 * <h3>注解元信息</h3>
 * <ul>
 *   <li>{@code @Retention(RUNTIME)} — 运行时保留，反射可读取</li>
 *   <li>{@code @Target(TYPE)} — 只能标注在类/接口/枚举上</li>
 * </ul>
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see com.example.annotation.Column
 * @see com.example.annotation.Id
 * @see com.example.JdbcUtils#getTableName(Class)
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {

    /**
     * 数据库表名。
     * <p>例如：{@code "users"}、{@code "order_items"}、{@code "goods_images"}</p>
     *
     * @return 对应的数据库表名（必需）
     */
    String value();
}
