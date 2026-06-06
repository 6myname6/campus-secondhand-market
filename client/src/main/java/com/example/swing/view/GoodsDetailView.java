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
 * 商品详情页 — 图片浏览、商品信息、收藏、购买、联系卖家。
 */
public class GoodsDetailView extends JPanel {

    private final ApiClient api;
    private final AppContext ctx = AppContext.getInstance();
    private final ChatStarter onChatStart;
    private final Runnable afterOrder;

    private JLabel imageLabel, titleLabel, priceLabel, origPriceLabel, statusLabel, viewLabel, favCountLabel, sellerLabel, descLabel;
    private JButton favBtn, buyBtn, chatBtn, prevBtn, nextBtn;
    private int goodsId, sellerId, currentImg;
    private boolean isFav;
    private List<String> imageUrls;

    public GoodsDetailView(ApiClient api, ChatStarter onChatStart, Runnable afterOrder) {
        this.api = api;
        this.onChatStart = onChatStart;
        this.afterOrder = afterOrder;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.setBackground(Color.WHITE);
        JButton back = new JButton("← 返回");
        back.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        back.addActionListener(e -> afterOrder.run());
        topBar.add(back);
        add(topBar, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(20, 10));
        center.setBackground(Color.WHITE);
        center.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));

        // 左侧图片区
        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.setBackground(Color.WHITE);
        imageLabel = new JLabel("", SwingConstants.CENTER);
        imageLabel.setPreferredSize(new Dimension(320, 280));
        imageLabel.setBackground(new Color(248, 248, 248));
        imageLabel.setOpaque(true);
        imageLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 64));
        imagePanel.add(imageLabel, BorderLayout.CENTER);

        // 图切换按钮
        JPanel imgBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        imgBtns.setBackground(Color.WHITE);
        prevBtn = new JButton("◀"); prevBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        prevBtn.addActionListener(e -> showImage(currentImg - 1));
        nextBtn = new JButton("▶"); nextBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        nextBtn.addActionListener(e -> showImage(currentImg + 1));
        imgBtns.add(prevBtn); imgBtns.add(nextBtn);
        imagePanel.add(imgBtns, BorderLayout.SOUTH);
        center.add(imagePanel, BorderLayout.WEST);

        // 右侧信息
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBackground(Color.WHITE);

        titleLabel = makeLabel("", 18, true);
        priceLabel = makeLabel("", 22, true); priceLabel.setForeground(new Color(220, 50, 50));
        origPriceLabel = makeLabel("", 14, false); origPriceLabel.setForeground(Color.GRAY);
        statusLabel = makeLabel("", 14, false);
        viewLabel = makeLabel("", 12, false); viewLabel.setForeground(Color.GRAY);
        favCountLabel = makeLabel("", 12, false); favCountLabel.setForeground(Color.GRAY);
        sellerLabel = makeLabel("", 12, false); sellerLabel.setForeground(new Color(70, 130, 255));
        descLabel = makeLabel("", 14, false);

        info.add(titleLabel); info.add(Box.createVerticalStrut(8));
        info.add(priceLabel); info.add(origPriceLabel);
        info.add(Box.createVerticalStrut(8));
        info.add(statusLabel); info.add(viewLabel); info.add(favCountLabel); info.add(sellerLabel);
        info.add(Box.createVerticalStrut(12));
        info.add(new JLabel("商品描述：") {{ setFont(new Font("微软雅黑", Font.BOLD, 14)); }});
        info.add(descLabel);
        info.add(Box.createVerticalStrut(20));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btns.setBackground(Color.WHITE);
        favBtn = new JButton("♡ 收藏");
        styleBtn(favBtn, new Color(255, 100, 100));
        favBtn.addActionListener(e -> toggleFav());
        buyBtn = new JButton("立即购买");
        styleBtn(buyBtn, new Color(70, 130, 255));
        buyBtn.addActionListener(e -> buy());
        chatBtn = new JButton("联系卖家");
        styleBtn(chatBtn, new Color(100, 180, 100));
        chatBtn.addActionListener(e -> startChat());
        btns.add(favBtn); btns.add(buyBtn); btns.add(chatBtn);
        info.add(btns);

        center.add(info, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    public void load(int goodsId) {
        this.goodsId = goodsId;
        this.currentImg = 0;
        this.imageUrls = null;
        imageLabel.setIcon(null); imageLabel.setText("📦");
        new SwingWorker<Map<String, Object>, Void>() {
            protected Map<String, Object> doInBackground() throws Exception {
                return api.getGoodsDetail(goodsId);
            }
            protected void done() {
                try {
                    Map<String, Object> g = get();
                    if (g == null) { JOptionPane.showMessageDialog(GoodsDetailView.this, "商品不存在或已下架"); return; }
                    titleLabel.setText(Objects.toString(g.get("title"), ""));
                    priceLabel.setText("¥" + Objects.toString(g.get("price"), "0"));
                    Object op = g.get("originalPrice");
                    origPriceLabel.setText(op != null ? "原价：¥" + op : "");
                    int status = ((Number) g.get("status")).intValue();
                    statusLabel.setText("状态：" + (status == 1 ? "在售" : status == 2 ? "已售" : "已下架"));
                    viewLabel.setText(((Number) g.get("viewCount")).intValue() + " 次浏览");
                    descLabel.setText("<html>" + Objects.toString(g.get("description"), "暂无描述") + "</html>");
                    sellerId = ((Number) g.get("sellerId")).intValue();
                    sellerLabel.setText("卖家：加载中...");
                    resolveSellerName(sellerId);
                    isFav = api.checkFavorite(ctx.getUserId(), goodsId);
                    favBtn.setText(isFav ? "♥ 已收藏" : "♡ 收藏");
                    buyBtn.setEnabled(status == 1 && sellerId != ctx.getUserId());
                    chatBtn.setEnabled(sellerId != ctx.getUserId());
                    // 加载收藏数
                    long favCount = api.getFavoriteCount(goodsId);
                    favCountLabel.setText(favCount + " 人收藏");
                    // 加载图片
                    loadImages();
                } catch (Exception e) { JOptionPane.showMessageDialog(GoodsDetailView.this, "加载失败: " + e.getMessage()); }
            }
        }.execute();
    }

    private void loadImages() {
        new SwingWorker<Void, Void>() {
            List<Map<String, Object>> imgs;
            protected Void doInBackground() throws Exception {
                imgs = api.getGoodsImages(goodsId);
                return null;
            }
            protected void done() {
                try {
                    get();
                    imageUrls = new java.util.ArrayList<>();
                    if (imgs != null) for (Map<String, Object> img : imgs) {
                        imageUrls.add((String) img.get("imageUrl"));
                    }
                    currentImg = 0;
                    showImage(0);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void showImage(int idx) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            imageLabel.setIcon(null); imageLabel.setText("📦");
            prevBtn.setEnabled(false); nextBtn.setEnabled(false);
            return;
        }
        if (idx < 0) idx = imageUrls.size() - 1;
        if (idx >= imageUrls.size()) idx = 0;
        currentImg = idx;
        prevBtn.setEnabled(imageUrls.size() > 1);
        nextBtn.setEnabled(imageUrls.size() > 1);

        String url = imageUrls.get(idx);
        new SwingWorker<ImageIcon, Void>() {
            protected ImageIcon doInBackground() throws Exception {
                Map<String, String> data = api.downloadImage(url);
                byte[] bytes = Base64.getDecoder().decode(data.get("base64"));
                ImageIcon icon = new ImageIcon(bytes);
                Image scaled = icon.getImage().getScaledInstance(320, 280, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }
            protected void done() {
                try { imageLabel.setIcon(get()); imageLabel.setText(""); }
                catch (Exception e) { imageLabel.setText("加载失败"); imageLabel.setIcon(null); }
            }
        }.execute();
    }

    private void toggleFav() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                if (isFav) api.removeFavorite(ctx.getUserId(), goodsId);
                else api.addFavorite(ctx.getUserId(), goodsId);
                return null;
            }
            protected void done() {
                try {
                    get(); isFav = !isFav;
                    favBtn.setText(isFav ? "♥ 已收藏" : "♡ 收藏");
                    long favCount = api.getFavoriteCount(goodsId);
                    favCountLabel.setText(favCount + " 人收藏");
                } catch (Exception e) { JOptionPane.showMessageDialog(GoodsDetailView.this, "操作失败: " + e.getMessage()); }
            }
        }.execute();
    }

    private void buy() {
        JTextField nameF = new JTextField(15), phoneF = new JTextField(15), addrF = new JTextField(30);
        JPanel p = new JPanel(new GridLayout(4, 2, 5, 5));
        p.add(new JLabel("收件人：")); p.add(nameF);
        p.add(new JLabel("电话：")); p.add(phoneF);
        p.add(new JLabel("地址：")); p.add(addrF);
        if (JOptionPane.showConfirmDialog(this, p, "确认购买", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String name = nameF.getText().trim(), phone = phoneF.getText().trim(), addr = addrF.getText().trim();
        if (name.isEmpty() || phone.isEmpty() || addr.isEmpty()) { JOptionPane.showMessageDialog(this, "请填写完整收件信息"); return; }
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                Map<String, Object> item = Map.of("goodsId", (Object) goodsId, "price", titleLabel.getText().replace("¥", ""), "quantity", 1);
                api.createOrder(ctx.getUserId(), name, phone, addr, null, List.of(item));
                return null;
            }
            protected void done() {
                try { get(); JOptionPane.showMessageDialog(GoodsDetailView.this, "下单成功！请前往订单页付款"); afterOrder.run(); } catch (Exception e) { JOptionPane.showMessageDialog(GoodsDetailView.this, "下单失败: " + e.getMessage()); }
            }
        }.execute();
    }

    private void startChat() {
        new SwingWorker<String[], Void>() {
            protected String[] doInBackground() throws Exception {
                int cid = api.getOrCreateConv(ctx.getUserId(), sellerId);
                String sname = api.resolveUsername(sellerId);
                return new String[]{String.valueOf(cid), sname};
            }
            protected void done() {
                try {
                    String[] r = get();
                    onChatStart.start(Integer.parseInt(r[0]), sellerId, r[1]);
                } catch (Exception e) { JOptionPane.showMessageDialog(GoodsDetailView.this, "创建会话失败: " + e.getMessage()); }
            }
        }.execute();
    }

    private JLabel makeLabel(String t, int size, boolean bold) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("微软雅黑", bold ? Font.BOLD : Font.PLAIN, size));
        l.setBackground(Color.WHITE);
        return l;
    }

    private void styleBtn(JButton btn, Color bg) {
        btn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        btn.setBackground(bg); btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
    }

    private void resolveSellerName(int userId) {
        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception { return api.resolveUsername(userId); }
            protected void done() {
                try { sellerLabel.setText("卖家：" + get()); } catch (Exception e) { sellerLabel.setText("卖家：用户" + userId); }
            }
        }.execute();
    }

    public interface ChatStarter { void start(int convId, int otherUserId, String otherName); }
}
