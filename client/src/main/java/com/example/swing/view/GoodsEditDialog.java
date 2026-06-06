package com.example.swing.view;

import com.example.swing.ApiClient;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/**
 * 商品编辑对话框。
 */
public class GoodsEditDialog extends JDialog {

    private final ApiClient api;
    private final int goodsId;
    private final Runnable onSaved;

    private JTextField titleField, priceField;
    private JTextArea descArea;

    public GoodsEditDialog(Window owner, ApiClient api, int goodsId,
                           String oldTitle, String oldPrice, Runnable onSaved) {
        super(owner, "编辑商品", ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.goodsId = goodsId;
        this.onSaved = onSaved;
        setSize(420, 360);
        setLocationRelativeTo(owner);

        JPanel form = new JPanel(null);
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
        Font f = new Font("微软雅黑", Font.PLAIN, 14);

        int y = 10;
        y = addRow(form, "标题：", titleField = new JTextField(oldTitle), f, y);
        y = addRow(form, "价格：", priceField = new JTextField(oldPrice), f, y);

        JLabel descL = new JLabel("描述："); descL.setFont(f); descL.setBounds(25, y, 55, 30);
        form.add(descL);
        descArea = new JTextArea(4, 20); descArea.setFont(f);
        JScrollPane sp = new JScrollPane(descArea); sp.setBounds(85, y, 290, 80);
        form.add(sp);
        y += 95;

        JButton saveBtn = new JButton("保存");
        saveBtn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        saveBtn.setBackground(new Color(70, 130, 255)); saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.setBounds(150, y, 120, 36);
        saveBtn.addActionListener(e -> save());
        form.add(saveBtn);

        add(form);
        setVisible(true);
    }

    private int addRow(JPanel p, String label, JTextField field, Font f, int y) {
        JLabel l = new JLabel(label); l.setFont(f); l.setBounds(25, y, 55, 30);
        p.add(l);
        field.setFont(f); field.setBounds(85, y, 290, 30);
        p.add(field);
        return y + 40;
    }

    private void save() {
        String t = titleField.getText().trim();
        String priceStr = priceField.getText().trim();
        if (t.isEmpty() || priceStr.isEmpty()) { JOptionPane.showMessageDialog(this, "请填写标题和价格"); return; }
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                BigDecimal price = new BigDecimal(priceStr);
                api.updateGoods(goodsId, t, descArea.getText().trim(), price, null);
                return null;
            }
            protected void done() {
                try { get(); onSaved.run(); dispose(); } catch (Exception e) { JOptionPane.showMessageDialog(GoodsEditDialog.this, "保存失败: " + e.getMessage()); }
            }
        }.execute();
    }
}
