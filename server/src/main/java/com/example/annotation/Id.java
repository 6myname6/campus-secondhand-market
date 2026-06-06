package com.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义 ORM 注解：标记实体类中的主键字段。
 *
 * <h3>设计目的</h3>
 * JdbcUtils 在执行 {@code updateById()} 和 {@code deleteById()} 时需要知道哪个字段是主键，
 * 以便生成正确的 WHERE 子句（如 {@code WHERE user_id = ?}）。
 * 标注此注解后，框架自动识别主键列进行定位。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class User {
 *     @Id                     // 标记为主键
 *     @Column("user_id")      // 对应的数据库列名
 *     private Integer userId;
 *
 *     private String username;
 * }
 * }</pre>
 *
 * <h3>工作机制</h3>
 * <ol>
 *   <li>JdbcUtils.getIdField() 遍历所有字段，查找标注了 {@code @Id} 的字段</li>
 *   <li>找到后在 UPDATE/DELETE 语句中以该字段作为 WHERE 条件</li>
 *   <li>若未找到 {@code @Id} 标注，<b>回退使用实体类的第一个字段</b>作为主键</li>
 * </ol>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>建议每个实体类只标注一个 {@code @Id} 字段</li>
 *   <li>通常与 {@code @Column} 配合使用（主键字段的列名往往与驼峰名不同）</li>
 *   <li>本注解无属性值，仅作为标记使用（Marker Annotation）</li>
 * </ul>
 *
 * <h3>注解元信息</h3>
 * <ul>
 *   <li>{@code @Retention(RUNTIME)} — 运行时保留</li>
 *   <li>{@code @Target(FIELD)} — 只能标注在字段上</li>
 * </ul>
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see com.example.annotation.Table
 * @see com.example.annotation.Column
 * @see com.example.JdbcUtils#updateById
 * @see com.example.JdbcUtils#deleteById
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {
    // 无属性，纯标记注解（Marker Annotation）
    // 功能：告诉 JdbcUtils 此字段是主键，用于定位 UPDATE/DELETE 的目标行
}
