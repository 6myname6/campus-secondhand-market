package com.example.entity;

import com.example.annotation.Column;
import com.example.annotation.Id;
import com.example.annotation.Table;

import java.time.LocalDateTime;

/**
 * 聊天消息实体类 — 映射到 {@code messages} 表。
 *
 * <h3>设计说明</h3>
 * 每条消息属于一个会话（conversation），记录发送者、接收者、消息内容。
 * 支持文本消息和图片消息（通过 {@code image_path} 字段）。
 *
 * <h3>消息已读机制</h3>
 * <ul>
 *   <li>{@code is_read = 0}：未读</li>
 *   <li>{@code is_read = 1}：已读</li>
 *   <li>发送消息时默认 is_read=0，并递增接收者在 conversation 中的未读计数</li>
 *   <li>接收者查看消息时，标记消息为已读，并清零 conversation 中的未读计数</li>
 * </ul>
 *
 * <h3>字段说明</h3>
 * <table>
 *   <tr><th>字段</th><th>数据库列</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>messageId</td><td>message_id</td><td>INT PK</td><td>消息ID，自增主键</td></tr>
 *   <tr><td>conversationId</td><td>conversation_id</td><td>INT FK</td><td>所属会话ID</td></tr>
 *   <tr><td>senderId</td><td>sender_id</td><td>INT FK</td><td>发送者用户ID</td></tr>
 *   <tr><td>receiverId</td><td>receiver_id</td><td>INT FK</td><td>接收者用户ID</td></tr>
 *   <tr><td>content</td><td>content</td><td>TEXT</td><td>消息文本内容</td></tr>
 *   <tr><td>imagePath</td><td>image_path</td><td>VARCHAR(500)</td><td>图片路径，可选</td></tr>
 *   <tr><td>sendTime</td><td>send_time</td><td>DATETIME</td><td>发送时间，自动填充</td></tr>
 *   <tr><td>isRead</td><td>is_read</td><td>TINYINT</td><td>是否已读：0=未读, 1=已读</td></tr>
 * </table>
 *
 * @author 李梓豪（组员李 — 基础架构层）
 * @see Conversation
 * @see com.example.service.MessageService
 * @since 1.0
 */
@Table("messages")
public class Message {

    /** 消息ID，自增主键（对应 message_id） */
    @Id
    @Column("message_id")
    private Integer messageId;

    /** 所属会话ID（对应 conversation_id） */
    @Column("conversation_id")
    private Integer conversationId;

    /** 发送者用户ID（对应 sender_id） */
    @Column("sender_id")
    private Integer senderId;

    /** 接收者用户ID（对应 receiver_id） */
    @Column("receiver_id")
    private Integer receiverId;

    /** 消息文本内容（对应 content 列） */
    private String content;

    /** 图片消息的存储路径，可选（对应 image_path） */
    @Column("image_path")
    private String imagePath;

    /** 消息发送时间，自动填充（对应 send_time） */
    @Column("send_time")
    private LocalDateTime sendTime;

    /** 是否已读：0=未读, 1=已读（对应 is_read） */
    @Column("is_read")
    private Integer isRead;

    /** 无参构造器（JdbcUtils 反射实例化需要） */
    public Message() {}

    // ==================== Getter / Setter ====================

    public Integer getMessageId() { return messageId; }
    public void setMessageId(Integer messageId) { this.messageId = messageId; }
    public Integer getConversationId() { return conversationId; }
    public void setConversationId(Integer conversationId) { this.conversationId = conversationId; }
    public Integer getSenderId() { return senderId; }
    public void setSenderId(Integer senderId) { this.senderId = senderId; }
    public Integer getReceiverId() { return receiverId; }
    public void setReceiverId(Integer receiverId) { this.receiverId = receiverId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public LocalDateTime getSendTime() { return sendTime; }
    public void setSendTime(LocalDateTime sendTime) { this.sendTime = sendTime; }
    public Integer getIsRead() { return isRead; }
    public void setIsRead(Integer isRead) { this.isRead = isRead; }
}
