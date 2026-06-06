package com.example.service;

import com.example.JdbcUtils;
import com.example.entity.Category;

import java.sql.Connection;
import java.util.List;

public class CategoryService extends BaseService {

    public List<Category> findAll() {
        return execTx(conn ->
                JdbcUtils.queryList(conn, Category.class,
                        "SELECT * FROM categories ORDER BY sort_order"));
    }

    public Category findById(int categoryId) {
        return execTx(conn ->
                JdbcUtils.queryOne(conn, Category.class,
                        "SELECT * FROM categories WHERE category_id = ?", categoryId));
    }

    public List<Category> findByParent(int parentId) {
        return execTx(conn ->
                JdbcUtils.queryList(conn, Category.class,
                        "SELECT * FROM categories WHERE parent_id = ? ORDER BY sort_order", parentId));
    }

    public int create(String name, int parentId, int sortOrder) {
        return execTx(conn ->
                JdbcUtils.insertAndGetKey(conn,
                        "INSERT INTO categories (category_name, parent_id, sort_order) VALUES (?, ?, ?)",
                        name, parentId, sortOrder));
    }

    public void update(int categoryId, String name, int sortOrder) {
        execTxVoid(conn -> {
            int rows = JdbcUtils.update(conn,
                    "UPDATE categories SET category_name = ?, sort_order = ? WHERE category_id = ?",
                    name, sortOrder, categoryId);
            if (rows == 0) throw new RuntimeException("分类不存在");
        });
    }

    public void delete(int categoryId) {
        execTxVoid(conn -> {
            int rows = JdbcUtils.update(conn,
                    "DELETE FROM categories WHERE category_id = ?", categoryId);
            if (rows == 0) throw new RuntimeException("分类不存在");
        });
    }
}
