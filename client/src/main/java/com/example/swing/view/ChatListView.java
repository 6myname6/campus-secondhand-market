package com.example.swing.view;

import com.example.swing.ApiClient;
import com.example.swing.AppContext;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.Objects;

/**
 * 聊天会话列表 — 含未读红点和用户名解析。
 */
public class ChatListView extends JPanel {

    private final ApiClient api;
    private final AppContext ctx = AppContext.getInstance();
    private final ChatStarter onChatStart;

    private final JPanel listPanel = new JPanel();

    public ChatListView(ApiClient api, ChatStarter onChatStart) {
        this.api = api;
        this.onChatStart = onChatStart;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JLabel title = new JLabel("聊天消息", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 20));
        title.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        add(title, BorderLayout.NORTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(Color.WHITE);
        add(new JScrollPane(listPanel), BorderLayout.CENTER);
    }

    public void refresh() {
        new SwingWorker<java.util.List<Map<String, Object>>, Void>() {
            protected java.util.List<Map<String, Object>> doInBackground() throws Exception {
                return api.getConversations(ctx.getUserId(), 1);
            }
            protected void done() {
                try {
                    java.util.List<Map<String, Object>> convs = get();
                    listPanel.removeAll();
                    if (convs == null || convs.isEmpty()) {
                        JLabel empty = new JLabel("暂无聊天会话", SwingConstants.CENTER);
                        empty.setFont(new Font("微软雅黑", Font.PLAIN, 14));
                        empty.setForeground(Color.GRAY);
                        listPanel.add(empty);
                    } else {
                        for (Map<String, Object> c : convs) {
                            listPanel.add(buildConvItem(c));
                        }
                    }
                    listPanel.revalidate(); listPanel.repaint();
                } catch (Exception e) {
                    listPanel.removeAll();
                    listPanel.add(new JLabel("加载失败: " + e.getMessage(), SwingConstants.CENTER));
                    listPanel.revalidate(); listPanel.repaint();
                }
            }
        }.execute();
    }

    private JPanel buildConvItem(Map<String, Object> c) {
        JPanel item = new JPanel(new BorderLayout(10, 0));
        item.setBackground(Color.WHITE);
        item.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 230)),
            BorderFactory.createEmptyBorder(10, 20, 10, 20)));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        int convId = ((Number) c.get("conversationId")).intValue();
        int myId = ctx.getUserId();
        int u1 = ((Number) c.get("user1Id")).intValue();
        int u2 = ((Number) c.get("user2Id")).intValue();
        int otherId = (u1 == myId) ? u2 : u1;

        // 占位用户名，异步加载真实用户名
        JLabel nameLabel = new JLabel("用户" + otherId);
        nameLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        resolveName(nameLabel, otherId);

        JLabel msgLabel = new JLabel(Objects.toString(c.get("lastMessage"), ""));
        msgLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        msgLabel.setForeground(Color.GRAY);

        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setBackground(Color.WHITE);
        text.add(nameLabel); text.add(msgLabel);

        item.add(text, BorderLayout.CENTER);

        // 未读数红点（实体类字段名为 unreadCntUser1 / unreadCntUser2）
        Object myUnreadObj = c.get(myId == u1 ? "unreadCntUser1" : "unreadCntUser2");
        int unread = myUnreadObj != null ? ((Number) myUnreadObj).intValue() : 0;
        if (unread > 0) {
            JLabel badge = new JLabel(String.valueOf(unread), SwingConstants.CENTER);
            badge.setFont(new Font("微软雅黑", Font.BOLD, 11));
            badge.setForeground(Color.WHITE);
            badge.setBackground(new Color(220, 50, 50));
            badge.setOpaque(true);
            badge.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
            badge.setPreferredSize(new Dimension(28, 22));
            item.add(badge, BorderLayout.EAST);
        }

        item.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                onChatStart.start(convId, otherId, nameLabel.getText());
            }
        });
        return item;
    }

    private void resolveName(JLabel label, int userId) {
        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception {
                return api.resolveUsername(userId);
            }
            protected void done() {
                try { label.setText(get()); } catch (Exception ignored) {}
            }
        }.execute();
    }

    public interface ChatStarter { void start(int convId, int otherUserId, String otherName); }
}
