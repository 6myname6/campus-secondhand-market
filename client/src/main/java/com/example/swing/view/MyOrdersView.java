package com.example.swing.view;

import com.example.swing.ApiClient;
import com.example.swing.AppContext;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 我的订单 — 买/卖双Tab。
 */
public class MyOrdersView extends JPanel {

    private final ApiClient api;
    private final AppContext ctx = AppContext.getInstance();
    private final OrderClickListener onOrderClick;

    private final JTabbedPane tabs = new JTabbedPane();
    private final DefaultTableModel buyModel = new NonEditableModel();
    private final DefaultTableModel sellModel = new NonEditableModel();
    private final JTable buyTable = new JTable(buyModel);
    private final JTable sellTable = new JTable(sellModel);

    public MyOrdersView(ApiClient api, OrderClickListener onOrderClick) {
        this.api = api;
        this.onOrderClick = onOrderClick;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        for (DefaultTableModel m : new DefaultTableModel[]{buyModel, sellModel}) {
            m.setColumnIdentifiers(new String[]{"订单号", "金额", "时间", "状态"});
        }
        for (JTable t : new JTable[]{buyTable, sellTable}) {
            t.setFont(new Font("微软雅黑", Font.PLAIN, 13));
            t.setRowHeight(28);
            t.getColumnModel().getColumn(0).setPreferredWidth(180);
            t.getColumnModel().getColumn(2).setPreferredWidth(160);
            t.getColumnModel().getColumn(3).setPreferredWidth(80);
            t.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    int row = t.getSelectedRow();
                    if (row >= 0) onOrderClick.onClick(((Number) t.getModel().getValueAt(row, 4)).intValue());
                }
            });
        }

        tabs.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        tabs.add("我买到的", new JScrollPane(buyTable));
        tabs.add("我卖出的", new JScrollPane(sellTable));
        add(tabs, BorderLayout.CENTER);
    }

    public void showTab(int idx) { tabs.setSelectedIndex(idx); }

    public void refresh() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                List<Map<String, Object>> buyOrders = api.getBuyerOrders(ctx.getUserId(), 1);
                List<Map<String, Object>> sellOrders = api.getSellerOrders(ctx.getUserId(), 1);
                SwingUtilities.invokeLater(() -> {
                    loadTable(buyModel, buyOrders);
                    loadTable(sellModel, sellOrders);
                });
                return null;
            }
            protected void done() { try { get(); } catch (Exception ignored) {} }
        }.execute();
    }

    private void loadTable(DefaultTableModel model, List<Map<String, Object>> orders) {
        model.setRowCount(0);
        if (orders == null) return;
        for (Map<String, Object> o : orders) {
            int status = ((Number) o.get("status")).intValue();
            model.addRow(new Object[]{
                Objects.toString(o.get("orderNo"), ""),
                "¥" + Objects.toString(o.get("totalAmount"), "0"),
                Objects.toString(o.get("createTime"), ""),
                statusName(status),
                o.get("orderId")
            });
        }
    }

    private String statusName(int s) {
        switch (s) { case 1: return "待支付"; case 2: return "待发货"; case 3: return "待收货"; case 4: return "已完成"; case 5: return "已取消"; default: return "未知"; }
    }

    private static class NonEditableModel extends DefaultTableModel {
        public boolean isCellEditable(int r, int c) { return false; }
    }

    public interface OrderClickListener { void onClick(int orderId); }
}
