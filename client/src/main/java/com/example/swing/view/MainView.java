package com.example.swing.view;

import com.example.swing.ApiClient;

import javax.swing.*;
import java.awt.*;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 首页 — 分类筛选 + 搜索 + 商品网格（含真实图片）。
 */
public class MainView extends JPanel {

    private final ApiClient api;
    private final GoodsClickListener onGoodsClick;

    private final JComboBox<String> categoryBox = new JComboBox<>();
    private final JTextField searchField = new JTextField(18);
    private final JPanel goodsGrid = new JPanel(new GridLayout(0, 4, 10, 10));
    private final JLabel pageLabel = new JLabel("", SwingConstants.CENTER);
    private final JButton prevBtn, nextBtn;
    private int currentPage = 1;
    private boolean hasMore = true;
    private String currentKeyword = "";
    private List<Map<String, Object>> cachedCategories;
    private boolean loadingCategories; // suppress combo listener during population

    public MainView(ApiClient api, GoodsClickListener onGoodsClick) {
        this.api = api;
        this.onGoodsClick = onGoodsClick;
        setLayout(new BorderLayout(0, 10));
        setBackground(Color.WHITE);

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 12));
        topBar.setBackground(Color.WHITE);

        JLabel catLabel = new JLabel("分类：");
        catLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        topBar.add(catLabel);
        categoryBox.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        categoryBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180)),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        categoryBox.setBackground(Color.WHITE);
        categoryBox.addItem("全部（加载中...）");
        categoryBox.addActionListener(e -> {
            if (loadingCategories) return;
            // 选择了分类 → 清除搜索关键词
            currentPage = 1;
            currentKeyword = "";
            searchField.setText("");
            loadGoods();
        });
        topBar.add(categoryBox);

        searchField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180)),
            BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        searchField.addActionListener(e -> doSearch());
        topBar.add(searchField);

        JButton searchBtn = new JButton("搜索");
        searchBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        searchBtn.addActionListener(e -> doSearch());
        topBar.add(searchBtn);

        add(topBar, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(goodsGrid);
        scroll.setBorder(null);
        goodsGrid.setBackground(Color.WHITE);
        add(scroll, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        bottomBar.setBackground(Color.WHITE);

        prevBtn = new JButton("上一页");
        prevBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        prevBtn.addActionListener(e -> { if (currentPage > 1) { currentPage--; loadGoods(); } });

        nextBtn = new JButton("下一页");
        nextBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        nextBtn.addActionListener(e -> { if (hasMore) { currentPage++; loadGoods(); } });

        pageLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        bottomBar.add(prevBtn);
        bottomBar.add(pageLabel);
        bottomBar.add(nextBtn);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void doSearch() {
        currentPage = 1;
        currentKeyword = searchField.getText().trim();
        // 清除分类选择（回到"全部"）
        if (!currentKeyword.isEmpty() && categoryBox.getSelectedIndex() > 0) {
            loadingCategories = true;
            categoryBox.setSelectedIndex(0);
            loadingCategories = false;
        }
        loadGoods();
    }

    public void refresh() {
        currentPage = 1;
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                cachedCategories = api.getCategories();
                return null;
            }
            protected void done() {
                try {
                    get();
                    loadingCategories = true;
                    categoryBox.removeAllItems();
                    categoryBox.addItem("全部");
                    if (cachedCategories != null) {
                        for (Map<String, Object> c : cachedCategories) {
                            categoryBox.addItem(Objects.toString(c.get("categoryName"), ""));
                        }
                    }
                    loadingCategories = false;
                } catch (Exception ignored) { loadingCategories = false; }
                loadGoods();
            }
        }.execute();
    }

    private void loadGoods() {
        goodsGrid.removeAll();
        goodsGrid.add(emptyLabel("加载中..."));
        goodsGrid.revalidate(); goodsGrid.repaint();

        new SwingWorker<List<Map<String, Object>>, Void>() {
            protected List<Map<String, Object>> doInBackground() throws Exception {
                if (!currentKeyword.isEmpty()) {
                    return api.searchGoods(currentKeyword, currentPage);
                }
                int catIdx = categoryBox.getSelectedIndex();
                if (catIdx <= 0 || cachedCategories == null || cachedCategories.isEmpty()) {
                    return api.searchGoods("", currentPage);
                }
                Map<String, Object> cat = cachedCategories.get(catIdx - 1);
                int catId = ((Number) cat.get("categoryId")).intValue();
                return api.getGoodsByCategory(catId, currentPage);
            }
            protected void done() {
                try {
                    List<Map<String, Object>> goods = get();
                    goodsGrid.removeAll();
                    if (goods == null || goods.isEmpty()) {
                        if (currentPage > 1) { currentPage--; loadGoods(); return; }
                        goodsGrid.add(emptyLabel("暂无商品"));
                    } else {
                        for (Map<String, Object> g : goods) goodsGrid.add(buildGoodsCard(g));
                    }
                    // 如果返回的数量等于每页上限，可能还有下一页
                    hasMore = goods != null && goods.size() >= 20;
                    goodsGrid.revalidate(); goodsGrid.repaint();
                    prevBtn.setEnabled(currentPage > 1);
                    nextBtn.setEnabled(hasMore);
                    pageLabel.setText("第 " + currentPage + " 页" + (hasMore ? "" : "（共 " + currentPage + " 页）"));
                } catch (Exception e) {
                    goodsGrid.removeAll();
                    goodsGrid.add(emptyLabel("加载失败: " + e.getMessage()));
                    goodsGrid.revalidate(); goodsGrid.repaint();
                }
            }
        }.execute();
    }

    private JPanel buildGoodsCard(Map<String, Object> g) {
        JPanel card = new JPanel(new BorderLayout(0, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel img = new JLabel("📦", SwingConstants.CENTER);
        img.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        img.setPreferredSize(new Dimension(180, 140));
        img.setOpaque(true);
        img.setBackground(new Color(248, 248, 248));
        card.add(img, BorderLayout.NORTH);

        int goodsId = ((Number) g.get("goodsId")).intValue();
        loadFirstImage(img, goodsId);

        JPanel info = new JPanel(new GridLayout(3, 1, 2, 2));
        info.setBackground(Color.WHITE);
        info.setBorder(BorderFactory.createEmptyBorder(5, 8, 8, 8));

        JLabel title = new JLabel(Objects.toString(g.get("title"), "无标题"));
        title.setFont(new Font("微软雅黑", Font.BOLD, 13));
        info.add(title);

        JLabel price = new JLabel("¥" + Objects.toString(g.get("price"), "0"));
        price.setFont(new Font("微软雅黑", Font.BOLD, 15));
        price.setForeground(new Color(220, 50, 50));
        info.add(price);

        JLabel views = new JLabel(Objects.toString(g.get("viewCount"), "0") + " 次浏览");
        views.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        views.setForeground(Color.GRAY);
        info.add(views);

        card.add(info, BorderLayout.CENTER);

        card.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) { onGoodsClick.onClick(goodsId); }
        });
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
                Image scaled = icon.getImage().getScaledInstance(180, 140, Image.SCALE_SMOOTH);
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

    private JLabel emptyLabel(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        l.setForeground(Color.GRAY);
        return l;
    }

    public interface GoodsClickListener { void onClick(int goodsId); }
}
