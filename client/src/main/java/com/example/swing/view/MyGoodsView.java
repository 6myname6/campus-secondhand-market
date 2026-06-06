package com.example.swing.view;

import com.example.swing.ApiClient;
import com.example.swing.AppContext;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 我发布的商品 — 商品管理列表，支持下架、编辑。
 */
public class MyGoodsView extends JPanel {

    private final ApiClient api;
    private final AppContext ctx = AppContext.getInstance();
    private final GoodsClickListener onGoodsClick;

    private final DefaultTableModel model = new NonEditableModel();
    private final JTable table = new JTable(model);
    private JButton offShelfBtn, editBtn, viewBtn;

    public MyGoodsView(ApiClient api, GoodsClickListener onGoodsClick) {
        this.api = api;
        this.onGoodsClick = onGoodsClick;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JLabel title = new JLabel("我发布的商品", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 20));
        title.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        add(title, BorderLayout.NORTH);

        model.setColumnIdentifiers(new String[]{"商品标题", "价格", "状态", "时间", "goodsId"});
        table.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setPreferredWidth(250);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);
        table.removeColumn(table.getColumnModel().getColumn(4)); // hide goodsId
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) viewGoods();
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setBackground(Color.WHITE);

        offShelfBtn = makeBtn("下架商品", new Color(220, 100, 80), () -> offShelf());
        editBtn = makeBtn("编辑商品", new Color(100, 160, 220), () -> editGoods());
        viewBtn = makeBtn("查看详情", new Color(70, 130, 255), () -> viewGoods());

        btnPanel.add(offShelfBtn);
        btnPanel.add(editBtn);
        btnPanel.add(viewBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }

    public void refresh() {
        new SwingWorker<List<Map<String, Object>>, Void>() {
            protected List<Map<String, Object>> doInBackground() throws Exception {
                return api.getSellerGoods(ctx.getUserId(), 1);
            }
            protected void done() {
                try {
                    List<Map<String, Object>> goods = get();
                    model.setRowCount(0);
                    if (goods != null) {
                        for (Map<String, Object> g : goods) {
                            int status = ((Number) g.get("status")).intValue();
                            model.addRow(new Object[]{
                                Objects.toString(g.get("title"), ""),
                                "¥" + Objects.toString(g.get("price"), "0"),
                                statusName(status),
                                Objects.toString(g.get("createTime"), ""),
                                ((Number) g.get("goodsId")).intValue()
                            });
                        }
                    }
                    updateButtons();
                } catch (Exception e) { JOptionPane.showMessageDialog(MyGoodsView.this, "加载失败: " + e.getMessage()); }
            }
        }.execute();
    }

    private int selectedGoodsId() {
        int row = table.getSelectedRow();
        if (row < 0) return -1;
        return (int) model.getValueAt(row, 4);
    }

    private int selectedStatus() {
        int row = table.getSelectedRow();
        if (row < 0) return -1;
        String s = (String) model.getValueAt(row, 2);
        if ("在售".equals(s)) return 1;
        if ("已售".equals(s)) return 2;
        return 3;
    }

    private void updateButtons() {
        boolean sel = table.getSelectedRow() >= 0;
        int status = selectedStatus();
        offShelfBtn.setEnabled(sel && status == 1);
        editBtn.setEnabled(sel && status == 1);
        viewBtn.setEnabled(sel);
    }

    private void offShelf() {
        int id = selectedGoodsId();
        if (id < 0) return;
        if (JOptionPane.showConfirmDialog(this, "确定下架该商品？", "确认", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { api.offShelf(id); return null; }
            protected void done() {
                try { get(); refresh(); } catch (Exception e) { JOptionPane.showMessageDialog(MyGoodsView.this, "下架失败: " + e.getMessage()); }
            }
        }.execute();
    }

    private void editGoods() {
        int id = selectedGoodsId();
        if (id < 0) return;
        int row = table.getSelectedRow();
        String oldTitle = (String) model.getValueAt(row, 0);
        String oldPrice = ((String) model.getValueAt(row, 1)).replace("¥", "");
        new GoodsEditDialog(SwingUtilities.windowForComponent(this), api, id, oldTitle, oldPrice, () -> refresh());
    }

    private void viewGoods() {
        int id = selectedGoodsId();
        if (id >= 0) onGoodsClick.onClick(id);
    }

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

    private String statusName(int s) {
        switch (s) { case 1: return "在售"; case 2: return "已售"; case 3: return "已下架"; default: return "未知"; }
    }

    private static class NonEditableModel extends DefaultTableModel {
        public boolean isCellEditable(int r, int c) { return false; }
    }

    public interface GoodsClickListener { void onClick(int goodsId); }
}
