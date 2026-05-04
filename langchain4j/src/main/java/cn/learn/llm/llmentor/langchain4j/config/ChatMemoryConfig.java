package cn.learn.llm.llmentor.langchain4j.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatMemory 配置类
 *
 * @author lianglei
 * @version 1.0
 * @date 2026/4/30
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
    }
}
