package com.example.swing.view;

import com.example.swing.ApiClient;
import com.example.swing.AppContext;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * 个人中心 — 查看/编辑资料 + 退出登录。
 */
public class ProfileView extends JPanel {

    private final ApiClient api;
    private final AppContext ctx = AppContext.getInstance();
    private final Runnable onLogout;

    private final JTextField phoneField = new JTextField(18);
    private final JTextField emailField = new JTextField(18);
    private final JLabel userLabel = new JLabel("", SwingConstants.CENTER);

    public ProfileView(ApiClient api, Runnable onLogout) {
        this.api = api;
        this.onLogout = onLogout;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JLabel title = new JLabel("个人中心", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 20));
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(null);
        form.setBackground(Color.WHITE);
        Font f = new Font("微软雅黑", Font.PLAIN, 14);

        userLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
        userLabel.setBounds(0, 20, 500, 40);
        form.add(userLabel);

        int y = 70;
        addRow(form, "手机号：", phoneField, f, y); y += 45;
        addRow(form, "邮  箱：", emailField, f, y); y += 55;

        JButton saveBtn = new JButton("保存修改");
        saveBtn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        saveBtn.setBounds(200, y, 130, 36);
        saveBtn.setBackground(new Color(70, 130, 255)); saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.addActionListener(e -> save());
        form.add(saveBtn);

        y += 55;
        JButton logoutBtn = new JButton("退出登录");
        logoutBtn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        logoutBtn.setBounds(200, y, 130, 36);
        logoutBtn.setBackground(new Color(220, 80, 80)); logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> {
            try { api.serverLogout(); } catch (Exception ignored) {}
            onLogout.run();
        });
        form.add(logoutBtn);

        add(form, BorderLayout.CENTER);
    }

    private void addRow(JPanel p, String label, JTextField field, Font f, int y) {
        JLabel l = new JLabel(label); l.setFont(f); l.setBounds(130, y, 80, 30);
        p.add(l);
        field.setFont(f); field.setBounds(210, y, 200, 30);
        p.add(field);
    }

    public void refresh() {
        userLabel.setText("当前用户：" + ctx.getUsername());
        new SwingWorker<java.util.Map<String, Object>, Void>() {
            protected java.util.Map<String, Object> doInBackground() throws Exception {
                return api.getUserById(ctx.getUserId());
            }
            protected void done() {
                try {
                    java.util.Map<String, Object> u = get();
                    phoneField.setText(Objects.toString(u.get("phone"), ""));
                    emailField.setText(Objects.toString(u.get("email"), ""));
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void save() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                api.updateProfile(ctx.getUserId(), phoneField.getText().trim(), emailField.getText().trim(), null);
                return null;
            }
            protected void done() {
                try { get(); JOptionPane.showMessageDialog(ProfileView.this, "保存成功"); } catch (Exception e) { JOptionPane.showMessageDialog(ProfileView.this, "保存失败: " + e.getMessage()); }
            }
        }.execute();
    }
}
