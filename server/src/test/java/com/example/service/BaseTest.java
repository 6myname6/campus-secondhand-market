package com.example.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * 测试基类：所有 Service 方法通过 BaseService.execTx 自行管理事务。
 * 这里只提供公共生命周期，不需要手动管理 Connection。
 */
public abstract class BaseTest {

    @BeforeEach
    void setUp() {
        // 不操作数据库
    }

    @AfterEach
    void tearDown() {
        // 每个 Service 方法内部的事务已自动回滚/提交，无需额外清理
    }
}
