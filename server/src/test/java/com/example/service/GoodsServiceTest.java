package com.example.service;

import com.example.entity.Goods;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GoodsServiceTest extends BaseTest {

    private final GoodsService goodsService = new GoodsService();
    private static int sellerId;
    private static int categoryId;

    @BeforeAll
    static void setupOnce() {
        String uid = "gt_" + UUID.randomUUID().toString().substring(0, 8);
        UserService us = new UserService();
        sellerId = us.register(uid, "pwd", null, null);
        CategoryService cs = new CategoryService();
        categoryId = cs.create("GT_" + uid, 0, 0);
    }

    @Test
    void testPublishAndFindById() {
        int goodsId = goodsService.publish(sellerId, categoryId,
                "iPhone 14 Test", "test", new BigDecimal("3999.00"), new BigDecimal("5999.00"));
        assertTrue(goodsId > 0);

        Goods goods = goodsService.findById(goodsId);
        assertNotNull(goods);
        assertEquals("iPhone 14 Test", goods.getTitle());
        assertEquals(new BigDecimal("3999.00"), goods.getPrice());
    }

    @Test
    void testSearch() {
        goodsService.publish(sellerId, categoryId, "华为Mate999", "desc", new BigDecimal("5999.00"), null);
        goodsService.publish(sellerId, categoryId, "小米14Test", "desc", new BigDecimal("3999.00"), null);

        List<Goods> results = goodsService.search("华为", 1, 10);
        assertTrue(results.size() >= 1, "搜索华为至少1条结果");
    }

    @Test
    void testOffShelf() {
        int goodsId = goodsService.publish(sellerId, categoryId, "待下架商品", "desc", new BigDecimal("100.00"), null);
        goodsService.offShelf(goodsId, sellerId);
        Goods goods = goodsService.findById(goodsId);
        assertEquals(3, goods.getStatus(), "status应为3(已下架)");
    }
}
