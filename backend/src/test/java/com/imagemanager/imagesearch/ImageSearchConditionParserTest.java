package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSearchConditionParserTest {

    private static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(2026, 10, 8, 12, 0, 0, 0, ImageSearchConditionParser.ZONE).toInstant(),
            ImageSearchConditionParser.ZONE);

    private static final ImageSearchCondition.Lexicon LEXICON = new ImageSearchCondition.Lexicon() {
        @Override
        public List<String> samplerNames() {
            return List.of("张三", "李四");
        }

        @Override
        public List<String> albumNames() {
            return List.of("滑雪服", "秋冬相册");
        }
    };

    @Test
    void mapsChinesePhrasesOntoRealFields() {
        ImageSearchCondition.Parsed parsed = ImageSearchConditionParser.parse(
                "要红色的针织连帽，2025年打样的，张三打的样，滑雪服相册里的，只看打样",
                LEXICON, CLOCK);

        assertEquals(ImageSearchFilters.SOURCE_GOODS, parsed.scope());
        assertEquals("2025年", parsed.time().label());
        assertEquals(ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ImageSearchConditionParser.ZONE)
                .toInstant().toEpochMilli(), parsed.time().fromEpochMs());
        assertEquals(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ImageSearchConditionParser.ZONE)
                .toInstant().toEpochMilli(), parsed.time().toEpochMs());
        assertEquals("张三", parsed.sampler());
        assertEquals("滑雪服", parsed.album());
        assertEquals(List.of("针织", "连帽", "红色"), parsed.attributes());
        assertTrue(parsed.chips().stream().noneMatch(chip -> "attr:滑雪服".equals(chip.id())));
    }

    @Test
    void relativeHalfYearAndExplicitScopes() {
        ImageSearchCondition.Parsed recent = ImageSearchConditionParser.parse("最近半年", LEXICON, CLOCK);
        ZonedDateTime now = ZonedDateTime.now(CLOCK);
        assertEquals("最近半年", recent.time().label());
        assertEquals(now.minusMonths(6).toInstant().toEpochMilli(), recent.time().fromEpochMs());

        assertEquals(ImageSearchFilters.SOURCE_LIBRARY,
                ImageSearchConditionParser.parse("只看素材", LEXICON, CLOCK).scope());
        assertEquals(ImageSearchFilters.SOURCE_GOODS,
                ImageSearchConditionParser.parse("只看打样", LEXICON, CLOCK).scope());
        assertEquals("2025年3月", ImageSearchConditionParser.parse("2025年3月", LEXICON, CLOCK).time().label());
    }

    @Test
    void ignoresNamesThatAreNotInTheLexicon() {
        ImageSearchCondition.Parsed parsed = ImageSearchConditionParser.parse(
                "王五打的样，不存在相册里的", LEXICON, CLOCK);
        assertNull(parsed.sampler());
        assertNull(parsed.album());
        assertTrue(parsed.chips().isEmpty());
    }

    @Test
    void nonConditionsStayEmpty() {
        for (String text : List.of("找同款", "这是什么货号", "分析一下这块面料", "你好", "找相似的素材")) {
            ImageSearchCondition.Parsed parsed = ImageSearchConditionParser.parse(text, LEXICON, CLOCK);
            assertTrue(parsed.chips().isEmpty(), text);
        }
    }

    @Test
    void hintCannotInventFields() {
        ImageSearchCondition.Hint invented = new ImageSearchCondition.Hint(
                "warehouse", "不存在", "虚构相册", List.of("荧光", "针织"));
        ImageSearchCondition.Parsed parsed = ImageSearchConditionParser.parse(
                "帮我看看", LEXICON, CLOCK, java.util.Set.of(), invented);
        assertNull(parsed.scope());
        assertNull(parsed.sampler());
        assertNull(parsed.album());
        assertEquals(List.of("针织"), parsed.attributes());

        ImageSearchCondition.Hint known = new ImageSearchCondition.Hint("goods", "李四", "秋冬相册", List.of());
        ImageSearchCondition.Parsed accepted = ImageSearchConditionParser.parse(
                "帮我看看", LEXICON, CLOCK, java.util.Set.of(), known);
        assertEquals(ImageSearchFilters.SOURCE_GOODS, accepted.scope());
        assertEquals("李四", accepted.sampler());
        assertEquals("秋冬相册", accepted.album());
    }

    @Test
    void excludeDropsAParsedChip() {
        ImageSearchCondition.Parsed parsed = ImageSearchConditionParser.parse(
                "要红色的，2025年", LEXICON, CLOCK, "time");
        assertNull(parsed.time());
        assertEquals(List.of("红色"), parsed.attributes());
    }

    @Test
    void clockInstantIsStable() {
        assertEquals(Instant.parse("2026-10-08T04:00:00Z"), CLOCK.instant());
    }
}
