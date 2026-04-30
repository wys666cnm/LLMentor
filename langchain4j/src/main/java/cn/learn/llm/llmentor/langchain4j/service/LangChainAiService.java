package cn.learn.llm.llmentor.langchain4j.service;

import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService
public interface LangChainAiService {

    String chat(String userMessage);

    Flux<String> chatStream(String userMessage);
}
