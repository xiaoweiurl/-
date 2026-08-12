package com.imagemanager.config;

import com.imagemanager.tools.QuotationAssistant;
import com.imagemanager.tools.QuotationTools;
import com.imagemanager.tools.SupplyChainAssistant;
import com.imagemanager.tools.SupplyChainTools;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 配置类
 * - 配置 Ollama ChatModel（复用现有 Ollama 实例）
 * - 创建 SupplyChainAssistant AI Service Bean（自动绑定 @Tool）
 */
@Configuration
public class LangChain4jConfig {

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ollama.chat-model:qwen3.6:35b}")
    private String chatModel;

    @Value("${app.ollama.timeout:60000}")
    private long timeoutMs;

    /**
     * Ollama ChatModel Bean
     * LangChain4j 通过此 Bean 与 Ollama 通信
     * temperature=0.3 降低随机性，提高 SQL 生成准确率
     */
    @Bean
    public ChatModel chatModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(chatModel)
                .timeout(Duration.ofMillis(timeoutMs))
                .numPredict(-1)
                .temperature(0.3)
                .build();
    }

    /**
     * 供应链 AI 助手 Bean
     * AiServices 自动将 @Tool 方法绑定到 AI Service 接口
     * 大模型会根据用户问题自动决定调用哪个工具
     */
    @Bean
    public SupplyChainAssistant supplyChainAssistant(ChatModel chatModel, SupplyChainTools tools) {
        return AiServices.builder(SupplyChainAssistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();
    }

    /**
     * 报价单 AI 助手 Bean
     * 绑定 QuotationTools（查询+确定性计算），大模型负责理解与解释
     */
    @Bean
    public QuotationAssistant quotationAssistant(ChatModel chatModel, QuotationTools quotationTools) {
        return AiServices.builder(QuotationAssistant.class)
                .chatModel(chatModel)
                .tools(quotationTools)
                .build();
    }
}
