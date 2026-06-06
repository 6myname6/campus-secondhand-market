package com.example.entity;

import com.example.annotation.Column;
import com.example.annotation.Id;
import com.example.annotation.Table;

/**
 * 用户实体类 — 映射到 {@code users} 表。
 *
 * <h3>字段说明</h3>
 * <table>
 *   <tr><th>字段</th><th>数据库列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>userId</td><td>user_id</td><td>INT PK</td><td>用户ID，自增主键</td></tr>
 *   <tr><td>username</td><td>username</td><td>VARCHAR(50) UNIQUE</td><td>用户名，唯一不可重复</td></tr>
 *   <tr><td>password</td><td>password</td><td>VARCHAR(255)</td><td>密码（BCrypt 哈希存储）</td></tr>
 *   <tr><td>phone</td><td>phone</td><td>VARCHAR(20)</td><td>手机号，可选</td></tr>
 *   <tr><td>email</td><td>email</td><td>VARCHAR(100)</td><td>邮箱，可选</td></tr>
 *   <tr><td>avatar</td><td>avatar</td><td>VARCHAR(500)</td><td>头像路径，默认值 /images/default_avatar.png</td></tr>
 *   <tr><td>role</td><td>role</td><td>ENUM('user','admin')</td><td>角色：普通用户 / 管理员</td></tr>
 *   <tr><td>status</td><td>status</td><td>TINYINT</td><td>状态：1=正常, 0=禁用</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>DATETIME</td><td>注册时间，自动填充</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>DATETIME</td><td>更新时间，自动更新</td></tr>
 * </table>
 *
 * <h3>ORM 映射说明</h3>
 * <ul>
 *   <li>{@code @Table("users")} — 映射到 users 表</li>
 *   <li>{@code @Id} — userId 是主键，用于 updateById/deleteById 定位</li>
 *   <li>{@code @Column} — 标注 Java 驼峰名与数据库蛇形名不同的字段</li>
 *   <li>{@code username/phone/email/avatar/role/status} 未标注 @Column，
 *       JdbcUtils 自动将驼峰转蛇形后直接匹配（恰好与数据库列名一致）</li>
 * </ul>
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see com.example.service.UserService
 * @see com.example.service.AuthService
 * @since 1.0
 */
@Table("users")
public class User {

    /** 用户ID，自增主键（对应数据库 user_id 列） */
    @Id
    @Column("user_id")
    private Integer userId;

    /** 用户名，唯一不可重复（自动匹配数据库 username 列） */
    private String username;

    /** 密码，BCrypt 加密存储（自动匹配数据库 password 列） */
    private String password;

    /** 手机号，可选（自动匹配数据库 phone 列） */
    private String phone;

    /** 邮箱地址，可选（自动匹配数据库 email 列） */
    private String email;

    /** 头像图片路径，默认 /images/default_avatar.png（自动匹配数据库 avatar 列） */
    private String avatar;

    /** 用户角色：user=普通用户, admin=管理员（自动匹配数据库 role 列） */
    private String role;

    /** 账户状态：1=正常启用, 0=禁用（自动匹配数据库 status 列） */
    private Integer status;

    /** 注册时间，自动填充（对应数据库 create_time 列） */
    @Column("create_time")
    private java.time.LocalDateTime createTime;

    /** 最后更新时间，自动更新（对应数据库 update_time 列） */
    @Column("update_time")
    private java.time.LocalDateTime updateTime;

    /** 无参构造器（JdbcUtils 反射实例化需要） */
    public User() {}

    // ==================== Getter / Setter ====================

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public java.time.LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
    public java.time.LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(java.time.LocalDateTime updateTime) { this.updateTime = updateTime; }
}
