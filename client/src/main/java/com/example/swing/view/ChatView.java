package com.example.swing.view;

import com.example.swing.ApiClient;
import com.example.swing.AppContext;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 聊天页 — 气泡消息 + 发送框 + 定时刷新 + 已读标记 + 删除。
 */
public class ChatView extends JPanel {

    private final ApiClient api;
    private final AppContext ctx = AppContext.getInstance();
    private final Runnable onBack;

    private final JPanel msgArea = new JPanel();
    private final JTextField inputField = new JTextField();
    private final JLabel chatName = new JLabel("聊天", SwingConstants.CENTER);
    private int conversationId, otherUserId;
    private int lastMsgId = 0;
    private Timer pollTimer;

    public ChatView(ApiClient api, Runnable onBack) {
        this.api = api;
        this.onBack = onBack;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(248, 248, 248));
        top.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));

        JButton back = new JButton("← 返回");
        back.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        back.addActionListener(e -> { stopPoll(); onBack.run(); });
        top.add(back, BorderLayout.WEST);

        chatName.setFont(new Font("微软雅黑", Font.BOLD, 16));
        top.add(chatName, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        msgArea.setLayout(new BoxLayout(msgArea, BoxLayout.Y_AXIS));
        msgArea.setBackground(Color.WHITE);
        JScrollPane sp = new JScrollPane(msgArea);
        sp.setBorder(null);
        add(sp, BorderLayout.CENTER);

        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setBackground(Color.WHITE);
        inputBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        inputField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        inputField.addActionListener(e -> send());
        inputBar.add(inputField, BorderLayout.CENTER);

        JButton sendBtn = new JButton("发送");
        sendBtn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        sendBtn.setBackground(new Color(70, 130, 255)); sendBtn.setForeground(Color.WHITE);
        sendBtn.setFocusPainted(false);
        sendBtn.addActionListener(e -> send());
        inputBar.add(sendBtn, BorderLayout.EAST);
        add(inputBar, BorderLayout.SOUTH);
    }

    public void open(int convId, int otherUserId, String otherName) {
        this.conversationId = convId;
        this.otherUserId = otherUserId;
        this.lastMsgId = 0;
        chatName.setText("与 " + otherName + " 聊天");
        msgArea.removeAll();
        msgArea.revalidate(); msgArea.repaint();
        loadMessages();
        startPoll();
        markAsRead();
        inputField.requestFocus();
    }

    private void startPoll() {
        stopPoll();
        pollTimer = new Timer(3000, e -> loadNewMessages());
        pollTimer.start();
    }

    private void stopPoll() { if (pollTimer != null) { pollTimer.stop(); pollTimer = null; } }

    private void markAsRead() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                api.markMessagesRead(conversationId);
                return null;
            }
            protected void done() { try { get(); } catch (Exception ignored) {} }
        }.execute();
    }

    private void loadMessages() {
        new SwingWorker<List<Map<String, Object>>, Void>() {
            protected List<Map<String, Object>> doInBackground() throws Exception {
                return api.getMessages(conversationId, 1);
            }
            protected void done() {
                try {
                    List<Map<String, Object>> msgs = get();
                    if (msgs != null && !msgs.isEmpty()) {
                        lastMsgId = ((Number) msgs.get(0).get("messageId")).intValue();
                        for (int i = msgs.size() - 1; i >= 0; i--) addBubble(msgs.get(i));
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void loadNewMessages() {
        new SwingWorker<List<Map<String, Object>>, Void>() {
            protected List<Map<String, Object>> doInBackground() throws Exception {
                return api.getMessages(conversationId, 1);
            }
            protected void done() {
                try {
                    List<Map<String, Object>> msgs = get();
                    if (msgs == null || msgs.isEmpty()) return;
                    boolean hasNew = false;
                    for (Map<String, Object> m : msgs) {
                        int mid = ((Number) m.get("messageId")).intValue();
                        if (mid > lastMsgId) { addBubble(m); lastMsgId = mid; hasNew = true; }
                    }
                    if (hasNew) { markAsRead(); msgArea.revalidate(); msgArea.repaint(); }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void addBubble(Map<String, Object> m) {
        boolean isMine = ((Number) m.get("senderId")).intValue() == ctx.getUserId();
        int msgId = ((Number) m.get("messageId")).intValue();
        String content = escapeHtml(Objects.toString(m.get("content"), ""));

        JPanel row = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 10, 2));
        row.setBackground(Color.WHITE);

        JPanel bubbleBox = new JPanel(new BorderLayout(2, 0));
        bubbleBox.setBackground(Color.WHITE);

        JLabel bubble = new JLabel("<html><div style='padding:8px 12px;max-width:280px;word-wrap:break-word;'>" + content + "</div></html>");
        bubble.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        bubble.setOpaque(true);
        bubble.setBackground(isMine ? new Color(70, 130, 255) : new Color(240, 240, 240));
        bubble.setForeground(isMine ? Color.WHITE : Color.BLACK);
        bubble.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        bubbleBox.add(bubble, BorderLayout.CENTER);

        if (isMine) {
            JLabel delLabel = new JLabel("✕");
            delLabel.setFont(new Font("微软雅黑", Font.PLAIN, 10));
            delLabel.setForeground(Color.LIGHT_GRAY);
            delLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            delLabel.setToolTipText("删除");
            delLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) { deleteMsg(msgId, row); }
            });
            bubbleBox.add(delLabel, BorderLayout.EAST);
        }

        row.add(bubbleBox);
        msgArea.add(row);
        msgArea.revalidate();
        JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, msgArea);
        if (sp != null) SwingUtilities.invokeLater(() -> sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum()));
    }

    private void deleteMsg(int msgId, JPanel row) {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { api.deleteMessage(msgId); return null; }
            protected void done() {
                try { get(); msgArea.remove(row); msgArea.revalidate(); msgArea.repaint(); } catch (Exception e) { JOptionPane.showMessageDialog(ChatView.this, "删除失败: " + e.getMessage()); }
            }
        }.execute();
    }

    private void send() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.setText("");
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { api.sendMessage(conversationId, ctx.getUserId(), otherUserId, text); return null; }
            protected void done() { try { get(); loadNewMessages(); } catch (Exception e) { JOptionPane.showMessageDialog(ChatView.this, "发送失败: " + e.getMessage()); } }
        }.execute();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
