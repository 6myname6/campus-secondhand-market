package com.example.service;

import com.example.JdbcUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * 事务管理抽象基类。
 *
 * 支持事务传播：当 execTx 内嵌套调用另一个 execTx 时，
 * 内层复用外层 Connection，不再开启新事务。
 */
public abstract class BaseService {

    /** 当前线程的事务连接（支持传播） */
    private static final ThreadLocal<Connection> TX_CONN = new ThreadLocal<>();

    /**
     * 在事务中执行业务逻辑，返回结果。
     *
     * 传播行为：如果当前线程已有活跃事务，直接复用连接（内层不提交/回滚）。
     * 否则从连接池获取新连接并管理事务生命周期。
     */
    protected <T> T execTx(Function<Connection, T> fn) {
        Connection existing = TX_CONN.get();
        if (existing != null) {
            // 传播：复用外层事务连接
            return fn.apply(existing);
        }

        Connection conn = null;
        try {
            conn = JdbcUtils.getConnection();
            conn.setAutoCommit(false);
            TX_CONN.set(conn);
            T result = fn.apply(conn);
            conn.commit();
            return result;
        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw new RuntimeException("Transaction failed", e);
        } finally {
            TX_CONN.remove();
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException ignored) {}
        }
    }

    /** 在事务中执行无返回值业务逻辑 */
    protected void execTxVoid(TransactionVoid fn) {
        execTx(conn -> {
            try {
                fn.execute(conn);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @FunctionalInterface
    protected interface TransactionVoid {
        void execute(Connection conn) throws Exception;
    }
}
