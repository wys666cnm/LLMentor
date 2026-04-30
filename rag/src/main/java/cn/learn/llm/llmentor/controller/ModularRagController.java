package cn.learn.llm.llmentor.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/27 17:29
 */
@RestController
@RequestMapping("/rag/modular")
public class ModularRagController implements InitializingBean {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private VectorStore vectorStore;

    private ChatClient chatClient;


    @GetMapping("/retriever")
    public String retriever(@RequestParam("query") String query) {
        //问题改写（富化、重写）(需要ChatClient，给一个大模型)
        QueryTransformer queryTransformer = RewriteQueryTransformer.builder()
                .promptTemplate(new PromptTemplate("""
                        Given a user query, rewrite it to provide better results when querying a {target}.
                        Remove any irrelevant information, and ensure the query is concise and specific.
                        
                        如果有表述不清的内容或者错别字，请修正。例如：“才箱” 修改为 “拆箱”
                        
                        Original query:
                        {query}
                        
                        Rewritten query:
                        """))
                .chatClientBuilder(ChatClient.builder(chatModel))
                .build();

        //问题扩写（多样化）(需要ChatClient，给一个大模型)
        QueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .numberOfQueries(3)
                .includeOriginal(true)
                .build();

        //问题增强（上下文）（构建新的提示词）
        QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .emptyContextPromptTemplate(new PromptTemplate("""
                        You are a helpful assistant that provides context for a user query.
                        {query}
                        """))
                .build();

        //向量语义相似度检索
        DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(5)
                .similarityThreshold(0.6)
                .build();


        //构建Modular RAG
        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(queryTransformer)
                .queryExpander(queryExpander)
                .queryAugmenter(queryAugmenter)
                .documentRetriever(retriever)
                .build();

        return chatClient.prompt(query).advisors(retrievalAugmentationAdvisor).call().content();
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        this.chatClient = ChatClient.builder(chatModel).build();
    }
}
