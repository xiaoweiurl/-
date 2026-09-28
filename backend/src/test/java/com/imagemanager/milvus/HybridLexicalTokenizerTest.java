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
