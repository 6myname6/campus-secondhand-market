package com.example.service;

import com.example.JdbcUtils;
import com.example.entity.Conversation;
import com.example.entity.Message;

import java.sql.Connection;
import java.util.List;

/**
 * 聊天消息服务。
 *
 * send 校验发送者是否为会话参与者，防止伪造。
 */
public class MessageService extends BaseService {

    private final ConversationService convService = new ConversationService();

    /**
     * 发送消息 — 校验 senderId 是会话参与者。
     * 在同一事务中：插入消息 + 更新会话最后消息/未读数。
     */
    public int send(int conversationId, int senderId, int receiverId,
                     String content, String imagePath) {
        return execTx(conn -> {
            // 校验发送者为会话参与者
            Conversation conv = JdbcUtils.queryOne(conn, Conversation.class,
                    "SELECT * FROM conversations WHERE conversation_id = ?", conversationId);
            if (conv == null) throw new RuntimeException("会话不存在");
            if (conv.getUser1Id() != senderId && conv.getUser2Id() != senderId) {
                throw new RuntimeException("不是会话参与者，无法发送消息");
            }
            // 校验接收者为对方
            int expectedReceiver = (conv.getUser1Id() == senderId) ? conv.getUser2Id() : conv.getUser1Id();
            if (expectedReceiver != receiverId) throw new RuntimeException("接收者信息不匹配");

            int msgId = JdbcUtils.insertAndGetKey(conn,
                    "INSERT INTO messages (conversation_id, sender_id, receiver_id, content, image_path) " +
                    "VALUES (?, ?, ?, ?, ?)",
                    conversationId, senderId, receiverId, content, imagePath);

            convService.updateLastMessage(conn, conversationId,
                    content != null ? content : "[图片]", receiverId);

            return msgId;
        });
    }

    /** 按会话分页查消息（时间倒序） */
    public List<Message> listByConversation(int conversationId, int page, int pageSize) {
        return execTx(conn ->
                JdbcUtils.queryPage(conn, Message.class,
                        "SELECT * FROM messages WHERE conversation_id = ? ORDER BY send_time DESC",
                        page, pageSize, conversationId));
    }

    /** 标记会话中某用户接收的消息为已读，同时清除会话未读数 */
    public void markRead(int conversationId, int userId) {
        execTxVoid(conn -> {
            JdbcUtils.update(conn,
                    "UPDATE messages SET is_read = 1 WHERE conversation_id = ? AND receiver_id = ? AND is_read = 0",
                    conversationId, userId);
            convService.markRead(conn, conversationId, userId);
        });
    }

    /** 删除消息 — 仅发送者可以删除自己的消息 */
    public void delete(int messageId, int userId) {
        execTxVoid(conn -> {
            int rows = JdbcUtils.update(conn,
                    "DELETE FROM messages WHERE message_id = ? AND sender_id = ?",
                    messageId, userId);
            if (rows == 0) throw new RuntimeException("消息不存在或无权删除");
        });
    }

    /** 用户总未读数 */
    public long countUnread(int userId) {
        return execTx(conn ->
                JdbcUtils.queryCount(conn,
                        "SELECT COUNT(*) FROM messages WHERE receiver_id = ? AND is_read = 0", userId));
    }
}
