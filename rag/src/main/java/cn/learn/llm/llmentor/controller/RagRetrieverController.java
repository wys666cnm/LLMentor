package cn.learn.llm.llmentor.controller;

import cn.learn.llm.llmentor.embedding.EmbeddingService;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG检索控制器
 *
 * @author lianglei
 * @version 1.0
 * @date 2026/4/24 21:40
 */
@RestController
@RequestMapping("/rag/retriever")
public class RagRetrieverController implements InitializingBean {


    private ChatClient chatClient;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private PgVectorStore vectorStore;

    @Autowired
    private EmbeddingService embeddingService;


    @GetMapping("/query")
    public String query(String query, double threshold) {
        List<Document> documents = embeddingService.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .similarityThreshold(threshold)
                        .build()
        );

        StringBuffer sb = new StringBuffer();
        for (Document document : documents) {
            sb.append(document.getText()).append("\n");
            sb.append("==========================");
        }

        return sb.toString();
    }

    /**
     * 自己拼装提示词
     *
     * @param query
     * @param threshold
     * @return
     */
    @GetMapping("/retrieve")
    public String retrieve(String query, double threshold) {
        //1、向量语义相似度检索
        List<Document> documents = embeddingService.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .similarityThreshold(threshold)
                        .build()
        );

        //2、提取文档内容
        String documentContent = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n=========文档分隔线===========\n\n"));


        //3、构建提示词模板
        String promptTemplate = """
                请基于以下提供的参考文档内容，回答用户的问题。
                如果参考文档中没有相关信息，请直接说明"没有找到相关信息"，不要编造内容。
                
                参考文档:
                {documents}
                
                用户问题: {question}
                
                输出格式：
                最终帮我汇总成一个markdown格式的答案，答案中请包含参考文档的标题和内容。最好是做一个排版，让答案更易读。
                例如
                一、
                 1.1、sajsa
                  1.1.1、sasas
                  .......
                 1.2、asasa
                  1.2.1、sasa
                二、
                 2.1、asasa
                 .......
                  2.1.1、asasa
                """;
        PromptTemplate prompt = new PromptTemplate(promptTemplate);
        Prompt realPrompt = prompt.create(Map.of("documents", documentContent, "question", query));
        return chatModel.call(realPrompt).getResult().getOutput().getText();
    }

    /**
     * 使用内置的提示词模板
     *
     * @param query
     * @return
     */
    @GetMapping("/retrieveAdvisor")
    public String retrieveAdvisor(String query) {
        return chatClient.prompt(query).call().content();
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        // 自定义Prompt模板
        PromptTemplate promptTemplate = new PromptTemplate("""
                请基于以下提供的参考文档内容，回答用户的问题。
                如果参考文档中没有相关信息，请直接说明"没有找到相关信息"，不要编造内容。
                
                参考文档内容:
                {question_answer_context}
                
                用户问题: {query}
                """);

        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().similarityThreshold(0.5).topK(5).build())
                .promptTemplate(promptTemplate).build();


        this.chatClient = ChatClient.builder(chatModel)
                // 实现 Logger 的 Advisor
                .defaultAdvisors(questionAnswerAdvisor)
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .withTopP(0.5)
                                .build()
                ).build();
    }
}
