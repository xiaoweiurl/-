package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatVisualSearchIntentTest {

    @Test
    void similarQuestionsUseTopHits() {
        for (String message : new String[]{
                "找相似款",
                "有没有类似的",
                "这款我们做过吗",
                "找历史打样",
                "这个是什么货号",
                "帮我搜同款"
        }) {
            assertEquals(ChatVisualSearchIntent.Decision.TOP, ChatVisualSearchIntent.decide(message), message);
        }
    }

    @Test
    void emptyOrShortQuestionSearches() {
        assertEquals(ChatVisualSearchIntent.Decision.TOP, ChatVisualSearchIntent.decide(null));
        assertEquals(ChatVisualSearchIntent.Decision.TOP, ChatVisualSearchIntent.decide(""));
        assertEquals(ChatVisualSearchIntent.Decision.TOP, ChatVisualSearchIntent.decide("   "));
        assertEquals(ChatVisualSearchIntent.Decision.TOP, ChatVisualSearchIntent.decide("看看"));
        assertEquals(ChatVisualSearchIntent.Decision.TOP, ChatVisualSearchIntent.decide("这是什么"));
        assertEquals(ChatVisualSearchIntent.Decision.TOP, ChatVisualSearchIntent.decide("?"));
    }

    @Test
    void otherImageQuestionsStayOnVision() {
        for (String message : new String[]{
                "请读出图片上的文字",
                "这张图有什么瑕疵",
                "分析一下这块面料的成分",
                "把图上的文字全部读出来",
                "请根据这张图帮我写一段秋冬通勤的卖点文案"
        }) {
            assertEquals(ChatVisualSearchIntent.Decision.SKIP, ChatVisualSearchIntent.decide(message), message);
        }
    }

    @Test
    void explicitAllOverridesTheDefaultCap() {
        assertEquals(ChatVisualSearchIntent.Decision.ALL, ChatVisualSearchIntent.decide("找相似款，全部列出来"));
        assertEquals(ChatVisualSearchIntent.Decision.ALL, ChatVisualSearchIntent.decide("所有类似的都列出来"));
        assertEquals(ChatVisualSearchIntent.Decision.ALL, ChatVisualSearchIntent.decide("不限数量找同款"));
        assertEquals(ChatVisualSearchIntent.Decision.ALL, ChatVisualSearchIntent.decide("都列出来"));
        assertEquals(ChatVisualSearchIntent.Decision.ALL, ChatVisualSearchIntent.decide("全部"));
    }

    @Test
    void allWordsDoNotOverrideANonVisualQuestion() {
        assertEquals(ChatVisualSearchIntent.Decision.SKIP, ChatVisualSearchIntent.decide("请把图上的文字全部翻译出来"));
        assertEquals(ChatVisualSearchIntent.Decision.SKIP, ChatVisualSearchIntent.decide("所有瑕疵都指出来"));
    }

    @Test
    void similarAskWinsWhenTheQuestionAlsoMentionsFabric() {
        assertEquals(ChatVisualSearchIntent.Decision.TOP, ChatVisualSearchIntent.decide("有没有类似的面料"));
    }
}
