package com.example.service;

import com.example.JdbcUtils;
import com.example.entity.GoodsImage;

import java.sql.Connection;
import java.util.List;

public class GoodsImageService extends BaseService {

    public List<GoodsImage> findByGoods(int goodsId) {
        return execTx(conn ->
                JdbcUtils.queryList(conn, GoodsImage.class,
                        "SELECT * FROM goods_images WHERE goods_id = ? ORDER BY sort_order", goodsId));
    }

    public int add(int goodsId, String imageUrl, int sortOrder) {
        return execTx(conn ->
                JdbcUtils.insertAndGetKey(conn,
                        "INSERT INTO goods_images (goods_id, image_url, sort_order) VALUES (?, ?, ?)",
                        goodsId, imageUrl, sortOrder));
    }

    public void addBatch(int goodsId, List<String> imageUrls) {
        execTxVoid(conn -> {
            for (int i = 0; i < imageUrls.size(); i++) {
                JdbcUtils.update(conn,
                        "INSERT INTO goods_images (goods_id, image_url, sort_order) VALUES (?, ?, ?)",
                        goodsId, imageUrls.get(i), i);
            }
        });
    }

    public void delete(int imageId) {
        execTxVoid(conn ->
                JdbcUtils.update(conn, "DELETE FROM goods_images WHERE image_id = ?", imageId));
    }

    public void deleteByGoods(int goodsId) {
        execTxVoid(conn ->
                JdbcUtils.update(conn, "DELETE FROM goods_images WHERE goods_id = ?", goodsId));
    }
}
