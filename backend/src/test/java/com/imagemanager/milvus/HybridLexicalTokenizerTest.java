package com.imagemanager.milvus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridLexicalTokenizerTest {

    @Test
    void productCodeStaysOneToken() {
        List<String> tokens = HybridLexicalTokenizer.tokens("货号25YK00022的面料");
        assertTrue(tokens.contains("25yk00022"));
        assertFalse(tokens.contains("25yk"));
        assertFalse(tokens.contains("00022"));
        assertFalse(tokens.contains("25"));
        assertEquals("25yk00022", HybridLexicalTokenizer.tokens("25YK00022").get(0));
        assertEquals(1, HybridLexicalTokenizer.tokens("25YK00022").size());
    }

    @Test
    void fabricCodeIsNotSplitApart() {
        List<String> tokens = HybridLexicalTokenizer.tokens("面料编号C100-40S");
        assertTrue(tokens.contains("c100-40s"), tokens.toString());
        assertTrue(tokens.contains("c10040s"), tokens.toString());
        assertFalse(tokens.contains("c"));
        assertFalse(tokens.contains("100"));
        assertTrue(tokens.stream().noneMatch(token -> token.chars().allMatch(Character::isDigit)));
    }

    @Test
    void sparseQueryKeepsOnlyTheCodeWhenTheQuestionHasOne() {
        assertEquals("k294", HybridLexicalTokenizer.sparseQuery("型号 K294"));
        assertEquals("m1yk010m", HybridLexicalTokenizer.sparseQuery("型号 M1YK010M"));
        assertEquals("t15", HybridLexicalTokenizer.sparseQuery("型号 T15"));
        String fabric = HybridLexicalTokenizer.sparseQuery("面料编号 C100-40S");
        assertTrue(fabric.contains("c100-40s"), fabric);
        assertFalse(fabric.contains("面料"), fabric);
        assertEquals(
                HybridLexicalTokenizer.lexicalText("棉质面料需要低温洗涤"),
                HybridLexicalTokenizer.sparseQuery("棉质面料需要低温洗涤"));
    }

    @Test
    void questionCodesDropUnitsExcelAndFiberCounts() {
        assertTrue(HybridLexicalTokenizer.isQuestionCode("25YK00022"));
        assertTrue(HybridLexicalTokenizer.isQuestionCode("C100-40S"));
        assertTrue(HybridLexicalTokenizer.isQuestionCode("M1YK010M"));
        assertTrue(HybridLexicalTokenizer.isQuestionCode("M2SW2516"));
        assertTrue(HybridLexicalTokenizer.isQuestionCode("ZX9K2"));
        assertFalse(HybridLexicalTokenizer.isQuestionCode("K294"));
        assertFalse(HybridLexicalTokenizer.isQuestionCode("T15"));
        assertFalse(HybridLexicalTokenizer.isQuestionCode("I413"));
        assertFalse(HybridLexicalTokenizer.isQuestionCode("J623"));
        assertFalse(HybridLexicalTokenizer.isQuestionCode("98g"));
        assertFalse(HybridLexicalTokenizer.isQuestionCode("405W"));
        assertFalse(HybridLexicalTokenizer.isQuestionCode("7865.00KGS"));
        assertFalse(HybridLexicalTokenizer.isQuestionCode("3D/12F"));
    }

    @Test
    void chineseBigramAndDictionaryTermSurvive() {
        List<String> tokens = HybridLexicalTokenizer.tokens("棉质面料的洗涤说明");
        assertTrue(tokens.contains("棉质面料") || tokens.contains("面料"), tokens.toString());
        assertTrue(tokens.contains("面料"), tokens.toString());
        assertFalse(tokens.contains("的"));
    }

    @Test
    void longTextKeepsCodeAtTheFront() {
        String lexical = HybridLexicalTokenizer.lexicalText("25YK00022" + "棉".repeat(20000));
        assertTrue(lexical.startsWith("25yk00022"));
        assertTrue(lexical.length() <= HybridLexicalTokenizer.MAX_LEXICAL_CHARS);
    }
}
