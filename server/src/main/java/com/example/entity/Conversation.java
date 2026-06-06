package com.example.entity;

import com.example.annotation.Column;
import com.example.annotation.Id;
import com.example.annotation.Table;

import java.time.LocalDateTime;

/**
 * 聊天会话实体类 — 映射到 {@code conversations} 表。
 *
 * <h3>设计说明</h3>
 * 两个用户之间的聊天通道。采用 <b>user1_id &lt; user2_id</b> 的约定，
 * 确保任意两个用户之间最多只有一个会话记录（配合 UNIQUE 约束保证）。
 *
 * <h3>为什么 user1_id 存较小值？</h3>
 * 这是常见的设计模式。当用户 A（ID=5）与用户 B（ID=3）聊天时：
 * <pre>
 *   user1_id = 3  (较小值)
 *   user2_id = 5  (较大值)
 * </pre>
 * 这样无论谁发起会话，都映射到同一条记录，避免重复创建。
 *
 * <h3>未读计数机制</h3>
 * 使用两个字段分别记录双方的未读数：
 * <ul>
 *   <li>{@code unread_cnt_user1} — user1 的未读消息数</li>
 *   <li>{@code unread_cnt_user2} — user2 的未读消息数</li>
 * </ul>
 * 当一方发送消息时，递增对方的未读计数；当对方查看会话时清零。
 *
 * <h3>字段说明</h3>
 * <table>
 *   <tr><th>字段</th><th>数据库列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>conversationId</td><td>conversation_id</td><td>INT PK</td><td>会话ID</td></tr>
 *   <tr><td>user1Id</td><td>user1_id</td><td>INT FK</td><td>较小ID的用户</td></tr>
 *   <tr><td>user2Id</td><td>user2_id</td><td>INT FK</td><td>较大ID的用户</td></tr>
 *   <tr><td>lastMessage</td><td>last_message</td><td>TEXT</td><td>最后一条消息摘要</td></tr>
 *   <tr><td>lastTime</td><td>last_time</td><td>DATETIME</td><td>最后消息时间</td></tr>
 *   <tr><td>unreadCntUser1</td><td>unread_cnt_user1</td><td>INT</td><td>user1 的未读计数</td></tr>
 *   <tr><td>unreadCntUser2</td><td>unread_cnt_user2</td><td>INT</td><td>user2 的未读计数</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>DATETIME</td><td>更新时间</td></tr>
 * </table>
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see Message
 * @see com.example.service.ConversationService
 * @since 1.0
 */
@Table("conversations")
public class Conversation {

    /** 会话ID，自增主键（对应 conversation_id） */
    @Id
    @Column("conversation_id")
    private Integer conversationId;

    /** 较小ID的用户（对应 user1_id） */
    @Column("user1_id")
    private Integer user1Id;

    /** 较大ID的用户（对应 user2_id） */
    @Column("user2_id")
    private Integer user2Id;

    /** 最后一条消息的文本摘要（对应 last_message） */
    @Column("last_message")
    private String lastMessage;

    /** 最后一条消息的时间（对应 last_time） */
    @Column("last_time")
    private LocalDateTime lastTime;

    /** user1 的未读消息计数（对应 unread_cnt_user1） */
    @Column("unread_cnt_user1")
    private Integer unreadCntUser1;

    /** user2 的未读消息计数（对应 unread_cnt_user2） */
    @Column("unread_cnt_user2")
    private Integer unreadCntUser2;

    /** 会话最后更新时间（对应 update_time） */
    @Column("update_time")
    private LocalDateTime updateTime;

    /** 无参构造器（JdbcUtils 反射实例化需要） */
    public Conversation() {}

    // ==================== Getter / Setter ====================

    public Integer getConversationId() { return conversationId; }
    public void setConversationId(Integer conversationId) { this.conversationId = conversationId; }
    public Integer getUser1Id() { return user1Id; }
    public void setUser1Id(Integer user1Id) { this.user1Id = user1Id; }
    public Integer getUser2Id() { return user2Id; }
    public void setUser2Id(Integer user2Id) { this.user2Id = user2Id; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public LocalDateTime getLastTime() { return lastTime; }
    public void setLastTime(LocalDateTime lastTime) { this.lastTime = lastTime; }
    public Integer getUnreadCntUser1() { return unreadCntUser1; }
    public void setUnreadCntUser1(Integer unreadCntUser1) { this.unreadCntUser1 = unreadCntUser1; }
    public Integer getUnreadCntUser2() { return unreadCntUser2; }
    public void setUnreadCntUser2(Integer unreadCntUser2) { this.unreadCntUser2 = unreadCntUser2; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
