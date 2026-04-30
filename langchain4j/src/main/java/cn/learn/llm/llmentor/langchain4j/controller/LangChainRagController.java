package cn.learn.llm.llmentor.langchain4j.controller;

import cn.learn.llm.llmentor.langchain4j.service.LangChainAiService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/30 16:40
 */
@RestController
@RequestMapping("/langchain/rag")
public class LangChainRagController {

    @Autowired
    OpenAiChatModel chatModel;

    @GetMapping("/retrieve")
    public String retrieve(@RequestParam("query") String query,
                           @RequestParam("filePath") String filePath, HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");

        //1、加载文档
        Document document = loadDocument(filePath, new ApacheTikaDocumentParser());

        //2、文档分块
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(
                300,
                50);
        List<TextSegment> textSegments = splitter.split(document);

        //3、向量化并存储
        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey("sk-7cce5d692dda4b64b4aa1e9d7ff1452b")
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .dimensions(768)
                .modelName("text-embedding-v4")
                .build();

        //4、生成embedding
        int batchSize = 9;
        List<Embedding> allEmbeddings = new ArrayList<>();
        for (int i = 0; i < textSegments.size(); i += batchSize) {
            List<TextSegment> segmentList = textSegments.subList(i, Math.min(i + batchSize, textSegments.size()));
            List<Embedding> embeddings = embeddingModel.embedAll(segmentList).content();
            allEmbeddings.addAll(embeddings);
        }

        //5、向量存储
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<TextSegment>();
        embeddingStore.addAll(allEmbeddings, textSegments);

        //6、构建上下文融合器
        DefaultContentInjector contentInjector = new DefaultContentInjector(new PromptTemplate("""
                ## 角色定位
                 你是一位专业的RAG问答助手。请根据提供的上下文信息，详细、准确地回答用户的问题。如果参考文档没有内容，请务必不要胡编乱造，请直接说明"没有找到相关信息"。
                
                 ## 任务要求：
                 1. 请基于以下提供的参考文档内容，回答用户的问题。
                 2. 如果参考文档中没有相关信息，请直接说明"没有找到相关信息"，不要编造内容。
                 3. 如果有了参考文档内容，请务必尽量回答问题。有可能用户的输入比较随意，你可以先尝试回答用户的问题，猜测他的实际需求，先给出回复，你需要尽量去贴合用户的问题需求。
                
                 ## 格式要求：
                 1. 你的所有回答必须使用Markdown格式进行排版。
                 2. 上下文信息中包含了图片描述标签，格式为：`<image src="URL" description="多模态描述"></image>`。
                 3. 如果图片与用户提问高度相关，请将此标签转换为标准的Markdown图片格式 `![图片](URL)`。
                 4. 仅在必要时包含图片，请注意千万不要输出重复的内容和图片，图片确保最终生成的URL不要重复。
                
                 ## 参考文档:
                {{contents}}
                
                 ## 用户问题:
                 {{userMessage}}
                
                 注意：如果参考文档下面的内容为空，请直接回答“没有找到相关信息”。
                """));


        //7、构建检索增强器
        DefaultRetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingModel(embeddingModel)
                        .embeddingStore(embeddingStore)
                        .maxResults(5)
                        .minScore(0.5)
                        .build())
                .contentInjector(contentInjector)
                .build();

        //8、构建最终的AI服务
        LangChainAiService langChainAiService = AiServices.builder(LangChainAiService.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        return langChainAiService.chat(query);
    }

    @RequestMapping("/retrieveSimple")
    public String retrieveSimple(@RequestParam("query") String query,
                                 @RequestParam("filePath") String filePath, HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");

        // 1. 配置 Embedding 模型
        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .modelName("text-embedding-v3")
                .dimensions(768)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .maxSegmentsPerBatch(9)
                .apiKey("sk-7cce5d692dda4b64b4aa1e9d7ff1452b")
                .build();

        // 2. 加载文档并生成 Embeddings
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300, 50))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(loadDocument(filePath, new ApacheTikaDocumentParser()));

        // 3. 构建 RAG 增强器（使用链式调用）
        DefaultRetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .maxResults(5)
                        .minScore(0.7)
                        .build())
                .contentInjector(new DefaultContentInjector(new PromptTemplate("""
                          ## 角色定位
                         你是一位专业的RAG问答助手。请根据提供的上下文信息，详细、准确地回答用户的问题。如果参考文档没有内容，请务必不要胡编乱造，请直接说明"没有找到相关信息"。
                        
                         ## 任务要求：
                         1. 请基于以下提供的参考文档内容，回答用户的问题。
                         2. 如果参考文档中没有相关信息，请直接说明"没有找到相关信息"，不要编造内容。
                         3. 如果有了参考文档内容，请务必尽量回答问题。有可能用户的输入比较随意，你可以先尝试回答用户的问题，猜测他的实际需求，先给出回复，你需要尽量去贴合用户的问题需求。
                        
                         ## 格式要求：
                         1. 你的所有回答必须使用Markdown格式进行排版。
                         2. 上下文信息中包含了图片描述标签，格式为：`<image src="URL" description="多模态描述"></image>`。
                         3. 如果图片与用户提问高度相关，请将此标签转换为标准的Markdown图片格式 `![图片](URL)`。
                         4. 仅在必要时包含图片，请注意千万不要输出重复的内容和图片，图片确保最终生成的URL不要重复。
                        
                         ## 参考文档:
                        {{contents}}
                        
                         ## 用户问题:
                         {{userMessage}}
                        
                         注意：如果参考文档下面的内容为空，请直接回答“没有找到相关信息”。
                        """)))
                .build();

        // 4. 构建 AI 服务并返回结果
        return AiServices.builder(LangChainAiService.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build()
                .chat(query);
    }

}
