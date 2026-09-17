package com.imagemanager.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplerWorkNoticeCardsTest {

    @Test
    void namedGoodsUsesGoodsNoAndProductNameAsHeadline() {
        GoodsSamplerNotice goods = new GoodsSamplerNotice(9L, "BN-001真丝吊带", "BN-001", "真丝吊带", "张三");
        assertEquals("BN-001 真丝吊带", SamplerWorkNoticeCards.productHeadline(goods));
        assertEquals("打样任务 · BN-001 真丝吊带", SamplerWorkNoticeCards.assignmentSessionTitle(goods));
        assertTrue(SamplerWorkNoticeCards.hasIdentity(goods));
    }

    @Test
    void unnamedFolderDoesNotAppearAsHeadline() {
        GoodsSamplerNotice goods = new GoodsSamplerNotice(77L, "未命名商品", "", "", "肖伟");
        assertEquals("待完善货号与品名", SamplerWorkNoticeCards.productHeadline(goods));
        assertEquals("打样任务 · 待完善货号与品名", SamplerWorkNoticeCards.assignmentSessionTitle(goods));
        assertFalse(SamplerWorkNoticeCards.hasIdentity(goods));
    }

    @Test
    void assignmentMarkdownHasHierarchyAndPendingPlaceholders() {
        GoodsSamplerNotice unnamed = new GoodsSamplerNotice(77L, "未命名商品", "  ", null, "肖伟");
        String md = SamplerWorkNoticeCards.assignmentMarkdown("肖伟", unnamed, "http://localhost:5000/sampler/77");
        assertTrue(md.startsWith("### 打样任务\n"));
        assertTrue(md.contains("**待完善货号与品名**"));
        assertTrue(md.contains("- **货号：** 待填写"));
        assertTrue(md.contains("- **品名：** 待填写"));
        assertTrue(md.contains("- **发起人：** 肖伟"));
        assertTrue(md.contains("- **打样员：** 肖伟"));
        assertFalse(md.contains("未填写"));
        assertFalse(md.contains("未命名商品"));
        assertTrue(md.contains("[立即填写打样表单](http://localhost:5000/sampler/77)"));
        assertTrue(md.contains("请打开表单补全货号和品名"));
    }

    @Test
    void assignmentMarkdownUsesRealFieldsAndOmitsEmptyOptionalRows() {
        GoodsSamplerNotice goods = new GoodsSamplerNotice(
                9L, "BN-001真丝吊带", "BN-001", "真丝吊带", "张三", "", "");
        String md = SamplerWorkNoticeCards.assignmentMarkdown("李四", goods, "http://x/sampler/9");
        assertTrue(md.contains("**BN-001 真丝吊带**"));
        assertTrue(md.contains("- **货号：** BN-001"));
        assertTrue(md.contains("- **品名：** 真丝吊带"));
        assertTrue(md.contains("您被指定为该商品的打样员。"));
        assertFalse(md.contains("待填写"));
        assertFalse(md.contains("**客户：**"));
        assertFalse(md.contains("**订单号：**"));
        assertEquals("立即填写打样表单", SamplerWorkNoticeCards.CTA_ASSIGN);
        assertTrue(SamplerWorkNoticeCards.CTA_ASSIGN.length() <= 20);
        assertTrue(SamplerWorkNoticeCards.CTA_FOLLOWUP.length() <= 20);
    }

    @Test
    void followupMarkdownIncludesFilledOptionalRows() {
        GoodsSamplerNotice goods = new GoodsSamplerNotice(
                9L, "BN-001真丝吊带", "BN-001", "真丝吊带", "张三", "Acme", "SO-1");
        String md = SamplerWorkNoticeCards.followupMarkdown("李四", goods, "http://x/sampler/9");
        assertTrue(md.startsWith("### 打样信息已更新\n"));
        assertTrue(md.contains("**BN-001 真丝吊带**"));
        assertTrue(md.contains("- **客户：** Acme"));
        assertTrue(md.contains("- **订单号：** SO-1"));
        assertTrue(md.contains("[查看打样表单](http://x/sampler/9)"));
        assertEquals("打样信息已更新 · BN-001 真丝吊带",
                SamplerWorkNoticeCards.followupSessionTitle(goods));
    }

    @Test
    void needsBackfillWhenIdentityAppearsAfterAssignment() {
        GoodsSamplerNotice sent = new GoodsSamplerNotice(1L, "未命名商品", "", "", "肖伟");
        GoodsSamplerNotice filled = new GoodsSamplerNotice(1L, "A1吊带", "A1", "吊带", "肖伟");
        assertTrue(SamplerWorkNoticeCards.needsBackfill(sent, filled));
        assertFalse(SamplerWorkNoticeCards.needsBackfill(filled, filled));
        assertFalse(SamplerWorkNoticeCards.needsBackfill(sent, sent));
    }

    @Test
    void sanitizesMarkdownMetacharactersInUserFields() {
        GoodsSamplerNotice goods = new GoodsSamplerNotice(1L, "", "A[1]", "吊带*款", "张]三");
        String md = SamplerWorkNoticeCards.assignmentMarkdown("李**四", goods, "http://x");
        assertTrue(md.contains("A［1］"));
        assertTrue(md.contains("吊带＊款"));
        assertFalse(md.contains("A[1]"));
        assertFalse(md.contains("吊带*款"));
    }

    @Test
    void toPlainTextStripsMarkdownMarkers() {
        String plain = SamplerWorkNoticeCards.toPlainText("### 打样任务\n\n**标题**\n\n- **货号：** BN-001");
        assertFalse(plain.contains("###"));
        assertFalse(plain.contains("**"));
        assertTrue(plain.contains("打样任务"));
        assertTrue(plain.contains("货号： BN-001"));
    }
}
