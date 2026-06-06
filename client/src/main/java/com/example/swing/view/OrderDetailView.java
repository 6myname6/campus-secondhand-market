package com.example.swing.view;

import com.example.swing.ApiClient;
import com.example.swing.AppContext;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.Objects;

/**
 * 订单详情页 — 完整信息 + 状态操作按钮。
 */
public class OrderDetailView extends JPanel {

    private final ApiClient api;
    private final AppContext ctx = AppContext.getInstance();
    private final Runnable afterAction;

    private final JPanel infoPanel = new JPanel();
    private final JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
    private int orderId, buyerId, sellerId, status;

    public OrderDetailView(ApiClient api, Runnable afterAction) {
        this.api = api;
        this.afterAction = afterAction;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setBackground(Color.WHITE);
        JButton back = new JButton("← 返回订单列表");
        back.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        back.addActionListener(e -> afterAction.run());
        top.add(back);
        add(top, BorderLayout.NORTH);

        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(Color.WHITE);
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));
        add(new JScrollPane(infoPanel), BorderLayout.CENTER);

        btnPanel.setBackground(Color.WHITE);
        add(btnPanel, BorderLayout.SOUTH);
    }

    public void load(int orderId) {
        this.orderId = orderId;
        new SwingWorker<Map<String, Object>, Void>() {
            protected Map<String, Object> doInBackground() throws Exception { return api.getOrderDetail(orderId); }
            protected void done() {
                try {
                    Map<String, Object> o = get();
                    if (o == null) { JOptionPane.showMessageDialog(OrderDetailView.this, "订单不存在"); return; }
                    status = ((Number) o.get("status")).intValue();
                    buyerId = ((Number) o.get("buyerId")).intValue();
                    sellerId = ((Number) o.get("sellerId")).intValue();

                    infoPanel.removeAll();
                    addLine("订单号：" + Objects.toString(o.get("orderNo"), ""));
                    addLine("金额：¥" + Objects.toString(o.get("totalAmount"), "0"));
                    addLine("状态：" + statusName(status));
                    addLine("收件人：" + Objects.toString(o.get("receiverName"), "") + "  " + Objects.toString(o.get("receiverPhone"), ""));
                    addLine("地址：" + Objects.toString(o.get("address"), ""));
                    addLine("物流：" + Objects.toString(o.get("logisticsCompany"), "无") + " " + Objects.toString(o.get("logisticsNo"), ""));
                    addLine("下单时间：" + Objects.toString(o.get("createTime"), ""));
                    addLine("");
                    addLine("——— 订单商品 ———");
                    // 加载订单商品明细
                    loadItems(orderId);
                    infoPanel.revalidate(); infoPanel.repaint();

                    btnPanel.removeAll();
                    int myId = ctx.getUserId();
                    if (status == 1 && myId == buyerId) { addBtn("付款", () -> act("pay")); addBtn("取消", () -> OrderDetailView.this.cancelOrder()); }
                    if (status == 2 && myId == sellerId) addBtn("发货", () -> ship());
                    if (status == 3 && myId == buyerId) addBtn("确认收货", () -> act("complete"));
                    if ((status == 1 || status == 2) && myId == buyerId) addBtn("取消订单", () -> OrderDetailView.this.cancelOrder());
                    btnPanel.revalidate(); btnPanel.repaint();
                } catch (Exception e) { JOptionPane.showMessageDialog(OrderDetailView.this, "加载失败: " + e.getMessage()); }
            }
        }.execute();
    }

    private void loadItems(int orderId) {
        new SwingWorker<java.util.List<Map<String, Object>>, Void>() {
            protected java.util.List<Map<String, Object>> doInBackground() throws Exception {
                return api.getOrderItems(orderId);
            }
            protected void done() {
                try {
                    java.util.List<Map<String, Object>> items = get();
                    if (items != null) for (Map<String, Object> item : items) {
                        addLine("  商品ID:" + item.get("goodsId") + "  ¥" + item.get("price") + " x" + item.get("quantity"));
                    }
                    infoPanel.revalidate(); infoPanel.repaint();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void addLine(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        l.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        infoPanel.add(l);
    }

    private void addBtn(String label, Runnable action) {
        JButton b = new JButton(label);
        b.setFont(new Font("微软雅黑", Font.BOLD, 14));
        b.setBackground(new Color(70, 130, 255)); b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        b.addActionListener(e -> action.run());
        btnPanel.add(b);
    }

    private void act(String action) {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                switch (action) {
                    case "pay": api.payOrder(orderId, ctx.getUserId()); break;
                    case "ship": ship(); return null;
                    case "complete": api.completeOrder(orderId, ctx.getUserId()); break;
                }
                return null;
            }
            protected void done() { try { get(); JOptionPane.showMessageDialog(OrderDetailView.this, "操作成功"); load(orderId); afterAction.run(); } catch (Exception e) { JOptionPane.showMessageDialog(OrderDetailView.this, "操作失败: " + e.getMessage()); } }
        }.execute();
    }

    private void ship() {
        JTextField comp = new JTextField(15), no = new JTextField(15);
        JPanel p = new JPanel(new GridLayout(2, 2, 5, 5));
        p.add(new JLabel("物流公司：")); p.add(comp);
        p.add(new JLabel("物流单号：")); p.add(no);
        if (JOptionPane.showConfirmDialog(this, p, "填写物流信息", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String c = comp.getText().trim(), n = no.getText().trim();
        if (c.isEmpty() || n.isEmpty()) { JOptionPane.showMessageDialog(this, "请填写完整"); return; }
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { api.shipOrder(orderId, c, n); return null; }
            protected void done() { try { get(); JOptionPane.showMessageDialog(OrderDetailView.this, "发货成功"); load(orderId); afterAction.run(); } catch (Exception e) { JOptionPane.showMessageDialog(OrderDetailView.this, "发货失败: " + e.getMessage()); } }
        }.execute();
    }

    private void cancelOrder() {
        String reason = JOptionPane.showInputDialog(this, "请输入取消原因（可选）：");
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { api.cancelOrder(orderId, ctx.getUserId(), reason); return null; }
            protected void done() { try { get(); JOptionPane.showMessageDialog(OrderDetailView.this, "订单已取消"); load(orderId); afterAction.run(); } catch (Exception e) { JOptionPane.showMessageDialog(OrderDetailView.this, "取消失败: " + e.getMessage()); } }
        }.execute();
    }

    private String statusName(int s) {
        switch (s) { case 1: return "待支付"; case 2: return "待发货"; case 3: return "待收货"; case 4: return "已完成"; case 5: return "已取消"; default: return "未知"; }
    }
}
