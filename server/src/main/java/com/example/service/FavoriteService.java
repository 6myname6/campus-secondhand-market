package com.example.service;

import com.example.JdbcUtils;
import com.example.entity.Favorite;
import com.example.entity.Goods;

import java.sql.Connection;
import java.util.List;

public class FavoriteService extends BaseService {

    public int add(int userId, int goodsId) {
        return execTx(conn ->
                JdbcUtils.insertAndGetKey(conn,
                        "INSERT INTO favorites (user_id, goods_id) VALUES (?, ?)", userId, goodsId));
    }

    public void remove(int userId, int goodsId) {
        execTxVoid(conn ->
                JdbcUtils.update(conn,
                        "DELETE FROM favorites WHERE user_id = ? AND goods_id = ?", userId, goodsId));
    }

    /** 查询用户的收藏商品列表（关联 goods 表） */
    public List<Goods> listByUser(int userId, int page, int pageSize) {
        return execTx(conn ->
                JdbcUtils.queryPage(conn, Goods.class,
                        "SELECT g.* FROM goods g INNER JOIN favorites f ON g.goods_id = f.goods_id " +
                        "WHERE f.user_id = ? ORDER BY f.create_time DESC",
                        page, pageSize, userId));
    }

    /** 检查某个商品是否被用户收藏 */
    public boolean check(int userId, int goodsId) {
        return execTx(conn ->
                JdbcUtils.queryCount(conn,
                        "SELECT COUNT(*) FROM favorites WHERE user_id = ? AND goods_id = ?",
                        userId, goodsId) > 0);
    }

    /** 某个商品被收藏的次数 */
    public long countByGoods(int goodsId) {
        return execTx(conn ->
                JdbcUtils.queryCount(conn,
                        "SELECT COUNT(*) FROM favorites WHERE goods_id = ?", goodsId));
    }
}
