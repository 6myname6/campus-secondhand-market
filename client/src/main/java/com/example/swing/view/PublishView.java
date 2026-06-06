package com.example.swing.view;

import com.example.swing.ApiClient;
import com.example.swing.AppContext;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 发布商品页 — 表单填写 + 图片选择上传。
 */
public class PublishView extends JPanel {

    private final ApiClient api;
    private final AppContext ctx = AppContext.getInstance();
    private final Runnable afterPublish;

    private JTextField titleField, priceField, origPriceField;
    private JTextArea descArea;
    private JComboBox<String> categoryBox;
    private java.util.List<Map<String, Object>> categories;

    private final List<File> selectedFiles = new ArrayList<>();
    private final JPanel imagePreview = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));

    public PublishView(ApiClient api, Runnable afterPublish) {
        this.api = api;
        this.afterPublish = afterPublish;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JLabel title = new JLabel("发布闲置商品", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 20));
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(null);
        form.setBackground(Color.WHITE);
        Font f = new Font("微软雅黑", Font.PLAIN, 14);
        int y = 10;

        y = addRow(form, "商品标题：", titleField = new JTextField(), f, y);
        y = addRow(form, "分　　类：", categoryBox = new JComboBox<>(), f, y);
        categoryBox.setFont(f);
        categoryBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180)),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        categoryBox.setBackground(Color.WHITE);
        y = addRow(form, "售　　价：", priceField = new JTextField(), f, y);
        y = addRow(form, "原　　价：", origPriceField = new JTextField(), f, y);

        JLabel descL = new JLabel("商品描述："); descL.setFont(f); descL.setBounds(60, y, 80, 30);
        form.add(descL);
        descArea = new JTextArea(5, 30); descArea.setFont(f);
        JScrollPane sp = new JScrollPane(descArea); sp.setBounds(145, y, 280, 100);
        form.add(sp);
        y += 115;

        // 图片选择
        JLabel imgL = new JLabel("商品图片："); imgL.setFont(f); imgL.setBounds(60, y, 80, 30);
        form.add(imgL);
        JButton selectBtn = new JButton("选择图片");
        selectBtn.setFont(f);
        selectBtn.setBounds(145, y, 100, 30);
        selectBtn.addActionListener(e -> selectImages());
        form.add(selectBtn);
        imagePreview.setBackground(Color.WHITE);
        imagePreview.setBounds(145, y + 35, 350, 60);
        form.add(imagePreview);
        y += 100;

        JButton submit = new JButton("发布商品");
        submit.setFont(new Font("微软雅黑", Font.BOLD, 14));
        submit.setBounds(200, y, 150, 36);
        submit.setBackground(new Color(70, 130, 255)); submit.setForeground(Color.WHITE);
        submit.setFocusPainted(false);
        submit.addActionListener(e -> publish());
        form.add(submit);

        add(form, BorderLayout.CENTER);
    }

    private int addRow(JPanel p, String label, JComponent field, Font f, int y) {
        JLabel l = new JLabel(label); l.setFont(f); l.setBounds(60, y, 80, 30);
        p.add(l);
        field.setFont(f); field.setBounds(145, y, 280, 30);
        p.add(field);
        return y + 42;
    }

    private void selectImages() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("图片文件", "jpg", "jpeg", "png", "gif"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                if (selectedFiles.size() >= 5) { JOptionPane.showMessageDialog(this, "最多选择5张图片"); break; }
                selectedFiles.add(f);
            }
            refreshPreview();
        }
    }

    private void refreshPreview() {
        imagePreview.removeAll();
        for (int i = 0; i < selectedFiles.size(); i++) {
            File f = selectedFiles.get(i);
            ImageIcon icon = new ImageIcon(new ImageIcon(f.getAbsolutePath()).getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH));
            JLabel thumb = new JLabel(icon);
            thumb.setToolTipText(f.getName());
            int idx = i;
            thumb.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    selectedFiles.remove(idx); refreshPreview();
                }
            });
            imagePreview.add(thumb);
        }
        imagePreview.revalidate(); imagePreview.repaint();
    }

    public void refresh() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { categories = api.getCategories(); return null; }
            protected void done() {
                try {
                    get();
                    categoryBox.removeAllItems();
                    if (categories != null) for (Map<String, Object> c : categories) categoryBox.addItem(Objects.toString(c.get("categoryName"), ""));
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void publish() {
        String t = titleField.getText().trim();
        String priceStr = priceField.getText().trim();
        if (t.isEmpty() || priceStr.isEmpty()) { JOptionPane.showMessageDialog(this, "请填写标题和售价"); return; }
        int catIdx = categoryBox.getSelectedIndex();
        if (catIdx < 0 || categories == null || catIdx >= categories.size()) { JOptionPane.showMessageDialog(this, "请选择分类"); return; }
        int catId = ((Number) categories.get(catIdx).get("categoryId")).intValue();

        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                BigDecimal price = new BigDecimal(priceStr);
                BigDecimal orig = origPriceField.getText().trim().isEmpty() ? null : new BigDecimal(origPriceField.getText().trim());
                int goodsId = api.publishGoods(ctx.getUserId(), catId, t, descArea.getText().trim(), price, orig);
                // 上传图片并关联到商品
                for (int i = 0; i < selectedFiles.size(); i++) {
                    File file = selectedFiles.get(i);
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    Map<String, String> result = api.uploadImage(base64, file.getName());
                    api.addGoodsImage(goodsId, result.get("url"), i);
                }
                return null;
            }
            protected void done() {
                try {
                    get();
                    titleField.setText(""); priceField.setText(""); origPriceField.setText(""); descArea.setText("");
                    selectedFiles.clear(); refreshPreview();
                    JOptionPane.showMessageDialog(PublishView.this, "发布成功！"); afterPublish.run();
                } catch (Exception e) { JOptionPane.showMessageDialog(PublishView.this, "发布失败: " + e.getMessage()); }
            }
        }.execute();
    }
}
