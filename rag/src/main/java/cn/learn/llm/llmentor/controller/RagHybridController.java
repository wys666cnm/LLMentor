package cn.learn.llm.llmentor.controller;

import cn.learn.llm.llmentor.embedding.EmbeddingService;
import cn.learn.llm.llmentor.es.ElasticSearchService;
import cn.learn.llm.llmentor.es.EsDocumentChunk;
import cn.learn.llm.llmentor.rerank.RerankUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 混合检索控制器
 *
 * @author lianglei
 * @version 1.0
 * @date 2026/4/26 21:08
 */
@Slf4j
@RestController
@RequestMapping("/rag/hybrid")
public class RagHybridController implements InitializingBean {

    private ChatClient chatClient;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ElasticSearchService elasticSearchService;

    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 从ES中搜索
     *
     * @param keyword
     * @return
     * @throws Exception
     */
    @RequestMapping("searchFromEs")
    public List<EsDocumentChunk> searchFromEs(@RequestParam("keyword") String keyword) throws Exception {
        return elasticSearchService.searchByKeyword(keyword);
    }

    /**
     * 从向量数据库中进行相似度语义搜索
     *
     * @param keyword
     * @return
     * @throws Exception
     */
    @RequestMapping("searchFromVector")
    public List<Document> searchFromVector(@RequestParam("keyword") String keyword) throws Exception {
        return embeddingService.similaritySearch(keyword);
    }

    /**
     * 混合检索
     *
     * @param query
     * @return
     * @throws Exception
     */
    @GetMapping("/hybridChat")
    public String hybridchat(@RequestParam("query") String query) throws Exception {
        log.info("========开始执行混合检索===========");
        // 1. 向量检索获取相似文档
        List<Document> vectorDocs = embeddingService.similaritySearch(query);
        log.info("向量查询检索到 {} 个相关文档，chunkId列表：{}",
                vectorDocs.size(),
                vectorDocs.stream()
                        .map(doc -> doc.getMetadata().getOrDefault("chunkId", "unknown").toString())
                        .collect(Collectors.joining(", ")));

        // 2. ES 关键词检索
        List<EsDocumentChunk> keywordDocs = elasticSearchService.searchByKeyword(query, 5, true);
        log.info("ES 关键词查询检索到 {} 个相关文档，chunkId列表：{}",
                keywordDocs.size(),
                keywordDocs.stream()
                        .map(doc -> doc.getMetadata().getOrDefault("chunkId", "unknown").toString())
                        .collect(Collectors.joining(", ")));

        // 3. 根据 id 去重并合并文档
        Map<String, String> idToContent = new LinkedHashMap<>();

        // 向量检索文档
        for (Document doc : vectorDocs) {
            idToContent.putIfAbsent(doc.getId(), doc.getText());
        }

        // ES 关键词检索文档
        for (EsDocumentChunk doc : keywordDocs) {
            idToContent.putIfAbsent(doc.getId(), doc.getContent());
        }

        List<String> mergedContents = RerankUtil.rrfFusion(vectorDocs, keywordDocs, 5);
        log.info("RRF 融合后共 {} 个相关文档块。", mergedContents.size());

//        List<String> mergedContents = new ArrayList<>(idToContent.values());
//        log.info("共检索到 {} 个相关文档块（向量 + 关键词融合）。", mergedContents.size());

        // 4. 构建提示词模板
        String promptTemplate = """
                请基于以下提供的参考文档内容，回答用户的问题。
                如果参考文档中没有相关信息，请直接说明"没有找到相关信息"，不要编造内容。
                如果有了参考文档内容，请务必尽量回答问题。有可能用户的输入比较随意，你可以先尝试回答用户的问题，猜测他的实际需求，先给出回复，你需要尽量去贴合用户的问题需求。
                
                参考文档:
                {documents}
                
                用户问题: {question}
                
                """;

        // 5. 拼接文档内容
        String documentContent = String.join("\n\n=========文档分隔线===========\n\n", mergedContents);
        log.info("查询到的文档信息：{}", documentContent);

        // 6. 填充模板参数
        PromptTemplate prompt = new PromptTemplate(promptTemplate);
        Prompt realPrompt = prompt.create(Map.of("documents", documentContent, "question", query));

        // 7. 调用大模型生成回答
        String text = chatClient.prompt(realPrompt).call().chatResponse().getResult().getOutput().getText();
        return text;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.chatClient = ChatClient.builder(chatModel)
                .build();
    }
}
