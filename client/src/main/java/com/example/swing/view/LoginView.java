package com.example.swing.view;

import com.example.swing.ApiClient;

import javax.swing.*;
import java.awt.*;

/**
 * 登录/注册页 — 双Tab切换，Enter键登录/注册。
 */
public class LoginView extends JPanel {

    private final ApiClient api;
    private final Runnable onLoginSuccess;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField loginUser = new JTextField(20);
    private final JPasswordField loginPass = new JPasswordField(20);
    private final JTextField regUser = new JTextField(20);
    private final JPasswordField regPass = new JPasswordField(20);
    private final JTextField regPhone = new JTextField(20);
    private final JTextField regEmail = new JTextField(20);

    public LoginView(ApiClient api, Runnable onLoginSuccess) {
        this.api = api;
        this.onLoginSuccess = onLoginSuccess;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JLabel title = new JLabel("校园二手交易平台", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 28));
        title.setBorder(BorderFactory.createEmptyBorder(40, 0, 30, 0));
        add(title, BorderLayout.NORTH);

        tabs.add("登录", buildLoginPanel());
        tabs.add("注册", buildRegPanel());
        tabs.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        add(tabs, BorderLayout.CENTER);

        JLabel hint = new JLabel("首次使用？请先注册账号", SwingConstants.CENTER);
        hint.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        hint.setForeground(Color.GRAY);
        hint.setBorder(BorderFactory.createEmptyBorder(10, 0, 30, 0));
        add(hint, BorderLayout.SOUTH);
    }

    private JPanel buildLoginPanel() {
        JPanel p = new JPanel(null);
        p.setBackground(Color.WHITE);

        Font f = new Font("微软雅黑", Font.PLAIN, 14);
        int y = 30;
        addField(p, "用户名：", loginUser, f, y); y += 45;
        addField(p, "密　码：", loginPass, f, y); y += 55;

        // Enter 键登录
        loginPass.addActionListener(e -> doLogin());

        JButton btn = new JButton("登 录");
        btn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        btn.setBounds(160, y, 200, 36);
        btn.setBackground(new Color(70, 130, 255));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.addActionListener(e -> doLogin());
        p.add(btn);

        return p;
    }

    private JPanel buildRegPanel() {
        JPanel p = new JPanel(null);
        p.setBackground(Color.WHITE);

        Font f = new Font("微软雅黑", Font.PLAIN, 14);
        int y = 20;
        addField(p, "用户名：", regUser, f, y); y += 45;
        addField(p, "密　码：", regPass, f, y); y += 45;
        addField(p, "手机号：", regPhone, f, y); y += 45;
        addField(p, "邮　箱：", regEmail, f, y); y += 55;

        // Enter 键注册
        regEmail.addActionListener(e -> doRegister());

        JButton btn = new JButton("注 册");
        btn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        btn.setBounds(160, y, 200, 36);
        btn.setBackground(new Color(255, 140, 50));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.addActionListener(e -> doRegister());
        p.add(btn);

        return p;
    }

    private void addField(JPanel p, String label, JTextField field, Font f, int y) {
        JLabel l = new JLabel(label);
        l.setFont(f); l.setBounds(80, y, 70, 30);
        p.add(l);
        field.setFont(f); field.setBounds(160, y, 200, 30);
        p.add(field);
    }

    private void doLogin() {
        String u = loginUser.getText().trim();
        String p = new String(loginPass.getPassword());
        if (u.isEmpty()) { JOptionPane.showMessageDialog(this, "请输入用户名"); return; }
        if (p.isEmpty()) { JOptionPane.showMessageDialog(this, "请输入密码"); return; }
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { api.login(u, p); return null; }
            protected void done() {
                try { get(); onLoginSuccess.run(); }
                catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    if (msg == null || msg.isEmpty()) msg = "登录失败，请检查用户名和密码";
                    JOptionPane.showMessageDialog(LoginView.this, msg, "登录失败", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void doRegister() {
        String u = regUser.getText().trim();
        String p = new String(regPass.getPassword());
        if (u.isEmpty()) { JOptionPane.showMessageDialog(this, "请输入用户名"); return; }
        if (p.isEmpty()) { JOptionPane.showMessageDialog(this, "请输入密码"); return; }
        if (p.length() < 4) { JOptionPane.showMessageDialog(this, "密码至少4位"); return; }
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                api.register(u, p, regPhone.getText().trim(), regEmail.getText().trim());
                return null;
            }
            protected void done() {
                try {
                    get();
                    regUser.setText(""); regPass.setText(""); regPhone.setText(""); regEmail.setText("");
                    JOptionPane.showMessageDialog(LoginView.this, "注册成功！请切换到登录标签登录");
                    tabs.setSelectedIndex(0);
                    loginUser.setText(u);
                    loginPass.requestFocus();
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    if (msg == null || msg.isEmpty()) msg = "注册失败";
                    JOptionPane.showMessageDialog(LoginView.this, msg, "注册失败", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
