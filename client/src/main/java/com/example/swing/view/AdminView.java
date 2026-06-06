package com.example.swing.view;

import com.example.swing.ApiClient;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理员面板 — 用户管理 + 分类管理。
 */
public class AdminView extends JPanel {

    private final ApiClient api;

    // 用户管理
    private final DefaultTableModel userModel = new NonEditableModel();
    private final JTable userTable = new JTable(userModel);
    private JButton disableBtn, enableBtn;

    // 分类管理
    private final DefaultTableModel catModel = new NonEditableModel();
    private final JTable catTable = new JTable(catModel);
    private List<Map<String, Object>> cachedCategories;
    private JButton catAddBtn, catEditBtn, catDelBtn;

    public AdminView(ApiClient api) {
        this.api = api;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JLabel title = new JLabel("管理员面板", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 20));
        title.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        tabs.add("用户管理", buildUserPanel());
        tabs.add("分类管理", buildCategoryPanel());
        add(tabs, BorderLayout.CENTER);
    }

    // ==================== 用户管理 ====================

    private JPanel buildUserPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);

        userModel.setColumnIdentifiers(new String[]{"用户ID", "用户名", "手机", "邮箱", "角色", "状态"});
        userTable.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        userTable.setRowHeight(26);
        userTable.getSelectionModel().addListSelectionListener(e -> updateUserBtns());
        p.add(new JScrollPane(userTable), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        btns.setBackground(Color.WHITE);
        enableBtn = makeBtn("启用", new Color(70, 180, 80), () -> setUserStatus(1));
        disableBtn = makeBtn("禁用", new Color(220, 80, 80), () -> setUserStatus(0));
        btns.add(enableBtn);
        btns.add(disableBtn);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private void loadUsers() {
        new SwingWorker<List<Map<String, Object>>, Void>() {
            protected List<Map<String, Object>> doInBackground() throws Exception {
                return api.listUsers(1);
            }
            protected void done() {
                try {
                    List<Map<String, Object>> users = get();
                    userModel.setRowCount(0);
                    if (users != null) for (Map<String, Object> u : users) {
                        userModel.addRow(new Object[]{
                            u.get("userId"), u.get("username"), Objects.toString(u.get("phone"), ""),
                            Objects.toString(u.get("email"), ""), u.get("role"), u.get("status")
                        });
                    }
                    updateUserBtns();
                } catch (Exception e) { JOptionPane.showMessageDialog(AdminView.this, "加载用户失败: " + e.getMessage()); }
            }
        }.execute();
    }

    private void updateUserBtns() {
        int row = userTable.getSelectedRow();
        if (row < 0) { enableBtn.setEnabled(false); disableBtn.setEnabled(false); return; }
        Object role = userModel.getValueAt(row, 4);
        boolean isAdmin = "admin".equals(role);
        enableBtn.setEnabled(!isAdmin);
        disableBtn.setEnabled(!isAdmin);
    }

    private void setUserStatus(int status) {
        int row = userTable.getSelectedRow();
        if (row < 0) return;
        int userId = ((Number) userModel.getValueAt(row, 0)).intValue();
        if ("admin".equals(userModel.getValueAt(row, 4))) { JOptionPane.showMessageDialog(this, "不能操作管理员账号"); return; }
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { api.updateUserStatus(userId, status); return null; }
            protected void done() {
                try { get(); loadUsers(); } catch (Exception e) { JOptionPane.showMessageDialog(AdminView.this, "操作失败: " + e.getMessage()); }
            }
        }.execute();
    }

    // ==================== 分类管理 ====================

    private JPanel buildCategoryPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);

        catModel.setColumnIdentifiers(new String[]{"分类ID", "分类名", "父级ID", "排序"});
        catTable.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        catTable.setRowHeight(26);
        catTable.getSelectionModel().addListSelectionListener(e -> updateCatBtns());
        p.add(new JScrollPane(catTable), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btns.setBackground(Color.WHITE);
        catAddBtn = makeBtn("新增", new Color(70, 180, 80), () -> catDialog(null));
        catEditBtn = makeBtn("编辑", new Color(100, 160, 220), () -> editCat());
        catDelBtn = makeBtn("删除", new Color(220, 80, 80), () -> deleteCat());
        catAddBtn.setEnabled(true);
        btns.add(catAddBtn); btns.add(catEditBtn); btns.add(catDelBtn);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private void loadCategories() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                cachedCategories = api.getCategories();
                return null;
            }
            protected void done() {
                try {
                    get();
                    catModel.setRowCount(0);
                    if (cachedCategories != null) for (Map<String, Object> c : cachedCategories) {
                        catModel.addRow(new Object[]{c.get("categoryId"), c.get("categoryName"), c.get("parentId"), c.get("sortOrder")});
                    }
                    updateCatBtns();
                } catch (Exception e) { JOptionPane.showMessageDialog(AdminView.this, "加载分类失败: " + e.getMessage()); }
            }
        }.execute();
    }

    private void updateCatBtns() {
        boolean sel = catTable.getSelectedRow() >= 0;
        catEditBtn.setEnabled(sel);
        catDelBtn.setEnabled(sel);
    }

    private void editCat() {
        int row = catTable.getSelectedRow();
        if (row < 0) return;
        Map<String, Object> cat = cachedCategories.get(row);
        catDialog(cat);
    }

    private void deleteCat() {
        int row = catTable.getSelectedRow();
        if (row < 0) return;
        if (JOptionPane.showConfirmDialog(this, "确定删除此分类？", "确认", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        int catId = ((Number) catModel.getValueAt(row, 0)).intValue();
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { api.deleteCategory(catId); return null; }
            protected void done() {
                try { get(); loadCategories(); } catch (Exception e) { JOptionPane.showMessageDialog(AdminView.this, "删除失败: " + e.getMessage()); }
            }
        }.execute();
    }

    private void catDialog(Map<String, Object> existing) {
        JTextField nameF = new JTextField(existing != null ? (String) existing.get("categoryName") : "", 15);
        JTextField sortF = new JTextField(existing != null ? String.valueOf(existing.get("sortOrder")) : "0", 5);
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("分类名：")); panel.add(nameF);
        panel.add(new JLabel("排序：")); panel.add(sortF);
        if (JOptionPane.showConfirmDialog(this, panel,
                existing != null ? "编辑分类" : "新增分类",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String name = nameF.getText().trim();
        if (name.isEmpty()) { JOptionPane.showMessageDialog(this, "请输入分类名"); return; }
        int sortVal = 0;
        try { sortVal = Integer.parseInt(sortF.getText().trim()); } catch (NumberFormatException ignored) {}
        final int sort = sortVal;
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                if (existing != null) {
                    api.updateCategory(((Number) existing.get("categoryId")).intValue(), name, sort);
                } else {
                    api.createCategory(name, 0, sort);
                }
                return null;
            }
            protected void done() {
                try { get(); loadCategories(); } catch (Exception e) { JOptionPane.showMessageDialog(AdminView.this, "操作失败: " + e.getMessage()); }
            }
        }.execute();
    }

    public void refresh() { loadUsers(); loadCategories(); }

    // ==================== helper ====================

    private JButton makeBtn(String label, Color bg, Runnable action) {
        JButton b = new JButton(label);
        b.setFont(new Font("微软雅黑", Font.BOLD, 13));
        b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        b.setEnabled(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static class NonEditableModel extends DefaultTableModel {
        public boolean isCellEditable(int r, int c) { return false; }
    }
}
