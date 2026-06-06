package com.example.service;

import com.example.JdbcUtils;
import com.example.entity.Goods;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品管理服务。
 *
 * 搜索使用 ESCAPE 子句防止 LIKE 通配符注入。
 */
public class GoodsService extends BaseService {

    /** 发布商品，返回商品ID */
    public int publish(int sellerId, int categoryId, String title, String description,
                        BigDecimal price, BigDecimal originalPrice) {
        return execTx(conn ->
                JdbcUtils.insertAndGetKey(conn,
                        "INSERT INTO goods (seller_id, category_id, title, description, price, original_price, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 1)",
                        sellerId, categoryId, title, description, price, originalPrice));
    }

    /** 根据ID查询（浏览数+1） */
    public Goods findById(int goodsId) {
        return execTx(conn -> {
            JdbcUtils.update(conn, "UPDATE goods SET view_count = view_count + 1 WHERE goods_id = ?", goodsId);
            return JdbcUtils.queryOne(conn, Goods.class,
                    "SELECT * FROM goods WHERE goods_id = ?", goodsId);
        });
    }

    /** 按分类分页查询（在售商品） */
    public List<Goods> findByCategory(int categoryId, int page, int pageSize) {
        return execTx(conn ->
                JdbcUtils.queryPage(conn, Goods.class,
                        "SELECT * FROM goods WHERE category_id = ? AND status = 1 ORDER BY create_time DESC",
                        page, pageSize, categoryId));
    }

    /**
     * 搜索商品 — ESCAPE 防止 LIKE 通配符注入。
     *
     * 客户端输入 _ 或 % 时不会作为通配符匹配，
     * 而是作为字面字符搜索。
     */
    public List<Goods> search(String keyword, int page, int pageSize) {
        return execTx(conn -> {
            String escaped = escapeLike(keyword);
            return JdbcUtils.queryPage(conn, Goods.class,
                    "SELECT * FROM goods WHERE status = 1 AND title LIKE ? ESCAPE '!' ORDER BY create_time DESC",
                    page, pageSize, "%" + escaped + "%");
        });
    }

    /** 转义 LIKE 特殊字符 % 和 _ */
    private static String escapeLike(String s) {
        if (s == null) return "";
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** 更新商品 */
    public void updateGoods(int goodsId, String title, String description,
                             BigDecimal price, Integer status) {
        execTxVoid(conn -> {
            int rows = JdbcUtils.update(conn,
                    "UPDATE goods SET title = ?, description = ?, price = ?, status = ? WHERE goods_id = ?",
                    title, description, price, status, goodsId);
            if (rows == 0) throw new RuntimeException("商品不存在");
        });
    }

    /** 下架商品（校验卖家身份） */
    public void offShelf(int goodsId, int sellerId) {
        execTxVoid(conn -> {
            int rows = JdbcUtils.update(conn,
                    "UPDATE goods SET status = 3 WHERE goods_id = ? AND seller_id = ?",
                    goodsId, sellerId);
            if (rows == 0) throw new RuntimeException("商品不存在或无权操作");
        });
    }

    /** 卖家商品列表 */
    public List<Goods> listBySeller(int sellerId, int page, int pageSize) {
        return execTx(conn ->
                JdbcUtils.queryPage(conn, Goods.class,
                        "SELECT * FROM goods WHERE seller_id = ? ORDER BY create_time DESC",
                        page, pageSize, sellerId));
    }
}
