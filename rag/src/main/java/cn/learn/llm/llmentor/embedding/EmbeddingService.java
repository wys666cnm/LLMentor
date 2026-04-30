package cn.learn.llm.llmentor.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/24 20:02
 */
@Service
public class EmbeddingService {

    /**
     * 返回的Top K结果数量
     */
    private static final int DEFAULT_TOP_K = 5;

    /**
     * 相似度阈值（限度）
     */
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    /**
     * 向量化
     *
     * @param documents 文档列表
     * @return
     */
    public List<float[]> embed(List<Document> documents) {
        return documents.stream().map(
                document -> embeddingModel.embed(document.getText())
        ).collect(Collectors.toList());
    }

    /**
     * 存储向量库
     *
     * @param documents 文档列表
     */
    public void embedAndSave(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return;
        }
        int batchSize = 9;
        for (int i = 0; i < documents.size(); i += batchSize) {
            List<Document> batches = documents.subList(i, Math.min(i + batchSize, documents.size()));
            vectorStore.add(batches);
        }
    }

    public List<Document> similaritySearch(String query) {
        return vectorStore.similaritySearch(SearchRequest
                .builder()
                .query(query)
                .topK(DEFAULT_TOP_K)
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                .build());
    }

    public List<Document> similaritySearch(SearchRequest searchRequest) {
        return vectorStore.similaritySearch(searchRequest);
    }
}
