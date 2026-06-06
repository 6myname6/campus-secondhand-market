package com.example.swing.view;

import com.example.swing.ApiClient;
import com.example.swing.AppContext;

import javax.swing.*;
import java.awt.*;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 收藏页 — 网格展示收藏商品（含真实图片）。
 */
public class FavoritesView extends JPanel {

    private final ApiClient api;
    private final AppContext ctx = AppContext.getInstance();
    private final MainView.GoodsClickListener onGoodsClick;

    private final JPanel grid = new JPanel(new GridLayout(0, 4, 10, 10));
    private int currentPage = 1;

    public FavoritesView(ApiClient api, MainView.GoodsClickListener onGoodsClick) {
        this.api = api;
        this.onGoodsClick = onGoodsClick;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JLabel title = new JLabel("我的收藏", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 20));
        title.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        add(title, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        grid.setBackground(Color.WHITE);
        add(scroll, BorderLayout.CENTER);
    }

    public void refresh() {
        new SwingWorker<List<Map<String, Object>>, Void>() {
            protected List<Map<String, Object>> doInBackground() throws Exception {
                return api.getFavorites(ctx.getUserId(), currentPage);
            }
            protected void done() {
                try {
                    List<Map<String, Object>> list = get();
                    grid.removeAll();
                    if (list == null || list.isEmpty()) {
                        JLabel empty = new JLabel("暂无收藏商品", SwingConstants.CENTER);
                        empty.setFont(new Font("微软雅黑", Font.PLAIN, 14));
                        empty.setForeground(Color.GRAY);
                        grid.add(empty);
                    } else {
                        for (Map<String, Object> g : list) {
                            JPanel card = buildCard(g);
                            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            int goodsId = ((Number) g.get("goodsId")).intValue();
                            card.addMouseListener(new java.awt.event.MouseAdapter() {
                                public void mouseClicked(java.awt.event.MouseEvent evt) { onGoodsClick.onClick(goodsId); }
                            });
                            grid.add(card);
                        }
                    }
                    grid.revalidate(); grid.repaint();
                } catch (Exception e) {
                    grid.removeAll();
                    grid.add(new JLabel("加载失败: " + e.getMessage(), SwingConstants.CENTER));
                    grid.revalidate(); grid.repaint();
                }
            }
        }.execute();
    }

    private JPanel buildCard(Map<String, Object> g) {
        JPanel card = new JPanel(new BorderLayout(0, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230)));

        JLabel img = new JLabel("📦", SwingConstants.CENTER);
        img.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        img.setPreferredSize(new Dimension(180, 120));
        img.setOpaque(true); img.setBackground(new Color(248, 248, 248));
        card.add(img, BorderLayout.NORTH);

        int goodsId = ((Number) g.get("goodsId")).intValue();
        loadFirstImage(img, goodsId);

        JPanel info = new JPanel(new GridLayout(2, 1));
        info.setBackground(Color.WHITE);
        info.setBorder(BorderFactory.createEmptyBorder(5, 8, 8, 8));

        JLabel t = new JLabel(Objects.toString(g.get("title"), "无标题"));
        t.setFont(new Font("微软雅黑", Font.BOLD, 13));
        info.add(t);

        JLabel p = new JLabel("¥" + Objects.toString(g.get("price"), "0"));
        p.setFont(new Font("微软雅黑", Font.BOLD, 14));
        p.setForeground(new Color(220, 50, 50));
        info.add(p);

        card.add(info, BorderLayout.CENTER);
        return card;
    }

    private void loadFirstImage(JLabel imgLabel, int goodsId) {
        new SwingWorker<ImageIcon, Void>() {
            protected ImageIcon doInBackground() throws Exception {
                List<Map<String, Object>> imgs = api.getGoodsImages(goodsId);
                if (imgs == null || imgs.isEmpty()) return null;
                String url = (String) imgs.get(0).get("imageUrl");
                Map<String, String> data = api.downloadImage(url);
                byte[] bytes = Base64.getDecoder().decode(data.get("base64"));
                ImageIcon icon = new ImageIcon(bytes);
                Image scaled = icon.getImage().getScaledInstance(180, 120, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) { imgLabel.setIcon(icon); imgLabel.setText(""); }
                } catch (Exception ignored) {}
            }
        }.execute();
    }
}
