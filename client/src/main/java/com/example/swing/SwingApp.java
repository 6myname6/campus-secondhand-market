package com.example.swing;

import com.example.swing.view.*;
import javax.swing.*;
import java.awt.*;

/**
 * 客户端主入口 — JFrame + CardLayout + JMenuBar 导航。
 */
public class SwingApp extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel = new JPanel(cardLayout);

    private final ApiClient api;
    private final AppContext ctx = AppContext.getInstance();

    private LoginView loginView;
    private MainView mainView;
    private GoodsDetailView goodsDetailView;
    private PublishView publishView;
    private MyOrdersView myOrdersView;
    private OrderDetailView orderDetailView;
    private MyGoodsView myGoodsView;
    private FavoritesView favoritesView;
    private ChatListView chatListView;
    private ChatView chatView;
    private ProfileView profileView;
    private AdminView adminView;

    public SwingApp(String host, int port) {
        setTitle("校园二手交易平台");
        setSize(960, 680);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        api = new ApiClient();
        try {
            api.connect(host, port);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "连接服务器失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }

        add(mainPanel, BorderLayout.CENTER);

        loginView = new LoginView(api, this::onLoginSuccess);
        mainPanel.add(loginView, "login");
        cardLayout.show(mainPanel, "login");
    }

    private void onLoginSuccess() {
        buildViews();
        setJMenuBar(buildMenuBar());
        revalidate();
        mainView.refresh(); // 首次加载分类和商品
        showPage("main");
    }

    private void buildViews() {
        mainView = new MainView(api, this::showGoodsDetail);
        goodsDetailView = new GoodsDetailView(api, this::showChat,
                () -> { myOrdersView.refresh(); showPage("myOrders"); });
        publishView = new PublishView(api, () -> { mainView.refresh(); showPage("main"); });
        myOrdersView = new MyOrdersView(api, this::showOrderDetail);
        orderDetailView = new OrderDetailView(api, () -> { myOrdersView.refresh(); showPage("myOrders"); });
        myGoodsView = new MyGoodsView(api, this::showGoodsDetail);
        favoritesView = new FavoritesView(api, this::showGoodsDetail);
        chatListView = new ChatListView(api, this::showChat);
        chatView = new ChatView(api, () -> { chatListView.refresh(); showPage("chatList"); });
        profileView = new ProfileView(api, this::logout);
        if (ctx.isAdmin()) {
            adminView = new AdminView(api);
        }

        mainPanel.add(mainView, "main");
        mainPanel.add(goodsDetailView, "goodsDetail");
        mainPanel.add(publishView, "publish");
        mainPanel.add(myOrdersView, "myOrders");
        mainPanel.add(orderDetailView, "orderDetail");
        mainPanel.add(myGoodsView, "myGoods");
        mainPanel.add(favoritesView, "favorites");
        mainPanel.add(chatListView, "chatList");
        mainPanel.add(chatView, "chat");
        mainPanel.add(profileView, "profile");
        if (adminView != null) mainPanel.add(adminView, "admin");
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        Font menuFont = new Font("微软雅黑", Font.PLAIN, 14);
        Font itemFont = new Font("微软雅黑", Font.PLAIN, 13);

        // 商品专区
        JMenu goodsMenu = new JMenu("商品专区"); goodsMenu.setFont(menuFont);
        mi(goodsMenu, "浏览全部商品", itemFont, () -> { mainView.refresh(); showPage("main"); });
        mi(goodsMenu, "发布闲置商品", itemFont, () -> { publishView.refresh(); showPage("publish"); });
        mi(goodsMenu, "我发布的", itemFont, () -> { myGoodsView.refresh(); showPage("myGoods"); });
        bar.add(goodsMenu);

        // 订单中心
        JMenu orderMenu = new JMenu("订单中心"); orderMenu.setFont(menuFont);
        mi(orderMenu, "我买到的", itemFont, () -> { myOrdersView.showTab(0); myOrdersView.refresh(); showPage("myOrders"); });
        mi(orderMenu, "我卖出的", itemFont, () -> { myOrdersView.showTab(1); myOrdersView.refresh(); showPage("myOrders"); });
        bar.add(orderMenu);

        // 我的收藏
        JMenu favMenu = new JMenu("我的收藏"); favMenu.setFont(menuFont);
        mi(favMenu, "收藏商品列表", itemFont, () -> { favoritesView.refresh(); showPage("favorites"); });
        bar.add(favMenu);

        // 消息聊天
        JMenu chatMenu = new JMenu("消息聊天"); chatMenu.setFont(menuFont);
        mi(chatMenu, "聊天会话", itemFont, () -> { chatListView.refresh(); showPage("chatList"); });
        bar.add(chatMenu);

        // 个人中心
        JMenu profileMenu = new JMenu("个人中心"); profileMenu.setFont(menuFont);
        mi(profileMenu, "个人资料修改", itemFont, () -> { profileView.refresh(); showPage("profile"); });
        mi(profileMenu, "退出登录", itemFont, this::logout);
        bar.add(profileMenu);

        // 管理员面板（仅管理员可见）
        if (ctx.isAdmin()) {
            JMenu adminMenu = new JMenu("管理"); adminMenu.setFont(menuFont);
            mi(adminMenu, "管理员面板", itemFont, () -> { adminView.refresh(); showPage("admin"); });
            bar.add(adminMenu);
        }

        return bar;
    }

    private void mi(JMenu menu, String label, Font f, Runnable action) {
        JMenuItem item = new JMenuItem(label); item.setFont(f);
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    public void showPage(String name) { cardLayout.show(mainPanel, name); }

    public void showGoodsDetail(int goodsId) { goodsDetailView.load(goodsId); showPage("goodsDetail"); }
    public void showOrderDetail(int orderId) { orderDetailView.load(orderId); showPage("orderDetail"); }
    public void showChat(int convId, int otherUserId, String otherName) { chatView.open(convId, otherUserId, otherName); showPage("chat"); }

    public void logout() {
        AppContext.getInstance().logout();
        mainPanel.removeAll();
        setJMenuBar(null);
        loginView = new LoginView(api, this::onLoginSuccess);
        mainPanel.add(loginView, "login");
        cardLayout.show(mainPanel, "login");
        revalidate();
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8081;
        final String h = host; final int p = port;
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
            new SwingApp(h, p).setVisible(true);
        });
    }
}
