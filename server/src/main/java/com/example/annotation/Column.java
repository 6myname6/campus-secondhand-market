package com.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义 ORM 注解：标记实体类字段对应的数据库列名。
 *
 * <h3>设计目的</h3>
 * 解决 Java 驼峰命名（如 {@code userId}）与数据库蛇形命名（如 {@code user_id}）
 * 不一致的问题。标注此注解后，JdbcUtils 在 INSERT/UPDATE/SELECT 时使用指定的列名。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class User {
 *     @Column("user_id")          // 明确指定列名为 user_id
 *     private Integer userId;
 *
 *     @Column("create_time")      // 明确指定列名为 create_time
 *     private LocalDateTime createTime;
 *
 *     private String username;    // 未标注时，自动驼峰转蛇形 → username
 *     private String email;       // 未标注时，自动驼峰转蛇形 → email
 * }
 * }</pre>
 *
 * <h3>工作机制</h3>
 * <ol>
 *   <li>若字段标注了 {@code @Column("列名")}，则使用指定的列名</li>
 *   <li>若字段未标注，则自动将字段名驼峰转蛇形作为列名</li>
 *   <li>JdbcUtils.getColumnFieldMap() 在首次访问时缓存映射结果</li>
 * </ol>
 *
 * <h3>注解元信息</h3>
 * <ul>
 *   <li>{@code @Retention(RUNTIME)} — 运行时保留，反射可读取</li>
 *   <li>{@code @Target(FIELD)} — 只能标注在类的成员字段上</li>
 * </ul>
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see com.example.annotation.Table
 * @see com.example.annotation.Id
 * @see com.example.JdbcUtils#getColumnFieldMap(Class)
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {

    /**
     * 数据库列名。
     * <p>例如：{@code "user_id"}、{@code "create_time"}、{@code "order_no"}</p>
     *
     * @return 对应的数据库列名（必需）
     */
    String value();
}
