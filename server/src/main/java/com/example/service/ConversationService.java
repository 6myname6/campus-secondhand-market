package com.example.service;

import com.example.JdbcUtils;
import com.example.entity.Conversation;

import java.sql.Connection;
import java.util.List;

/**
 * 聊天会话服务。
 *
 * createOrGet 使用 INSERT IGNORE 消除竞态：
 * 两个并发请求同时为同一用户对创建会话时，不再抛出唯一约束异常。
 */
public class ConversationService extends BaseService {

    public int createOrGet(int userId1, int userId2) {
        return execTx(conn -> createOrGet(conn, userId1, userId2));
    }

    public List<Conversation> listByUser(int userId, int page, int pageSize) {
        return execTx(conn ->
                JdbcUtils.queryPage(conn, Conversation.class,
                        "SELECT * FROM conversations " +
                        "WHERE user1_id = ? OR user2_id = ? " +
                        "ORDER BY last_time DESC",
                        page, pageSize, userId, userId));
    }

    public void updateLastMessage(int conversationId, String content, int targetUserId) {
        execTxVoid(conn -> updateLastMessage(conn, conversationId, content, targetUserId));
    }

    public void markRead(int conversationId, int userId) {
        execTxVoid(conn -> markRead(conn, conversationId, userId));
    }

    // ===== 事务内部方法（接受 Connection，不开启新事务） =====

    int createOrGet(Connection conn, int userId1, int userId2) {
        int smaller = Math.min(userId1, userId2);
        int larger = Math.max(userId1, userId2);

        // 先查是否已有会话
        Conversation conv = JdbcUtils.queryOne(conn, Conversation.class,
                "SELECT * FROM conversations WHERE user1_id = ? AND user2_id = ?", smaller, larger);
        if (conv != null) return conv.getConversationId();

        // 使用 INSERT IGNORE 防止并发重复创建
        int rows = JdbcUtils.update(conn,
                "INSERT IGNORE INTO conversations (user1_id, user2_id) VALUES (?, ?)", smaller, larger);
        if (rows > 0) {
            return JdbcUtils.queryOne(conn, Integer.class,
                    "SELECT LAST_INSERT_ID()");
        }
        // 并发情况下其他请求已插入，重新查询
        conv = JdbcUtils.queryOne(conn, Conversation.class,
                "SELECT * FROM conversations WHERE user1_id = ? AND user2_id = ?", smaller, larger);
        if (conv != null) return conv.getConversationId();
        throw new RuntimeException("创建会话失败");
    }

    void updateLastMessage(Connection conn, int conversationId, String content, int targetUserId) {
        Conversation conv = JdbcUtils.queryOne(conn, Conversation.class,
                "SELECT * FROM conversations WHERE conversation_id = ?", conversationId);
        if (conv == null) return;

        if (targetUserId == conv.getUser1Id()) {
            JdbcUtils.update(conn,
                    "UPDATE conversations SET last_message = ?, last_time = NOW(), " +
                    "unread_cnt_user1 = unread_cnt_user1 + 1 WHERE conversation_id = ?",
                    content, conversationId);
        } else {
            JdbcUtils.update(conn,
                    "UPDATE conversations SET last_message = ?, last_time = NOW(), " +
                    "unread_cnt_user2 = unread_cnt_user2 + 1 WHERE conversation_id = ?",
                    content, conversationId);
        }
    }

    void markRead(Connection conn, int conversationId, int userId) {
        Conversation conv = JdbcUtils.queryOne(conn, Conversation.class,
                "SELECT * FROM conversations WHERE conversation_id = ?", conversationId);
        if (conv == null) return;

        if (userId == conv.getUser1Id()) {
            JdbcUtils.update(conn,
                    "UPDATE conversations SET unread_cnt_user1 = 0 WHERE conversation_id = ?", conversationId);
        } else {
            JdbcUtils.update(conn,
                    "UPDATE conversations SET unread_cnt_user2 = 0 WHERE conversation_id = ?", conversationId);
        }
    }
}
