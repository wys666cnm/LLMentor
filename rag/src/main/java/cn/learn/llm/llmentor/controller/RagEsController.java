package cn.learn.llm.llmentor.controller;

import cn.learn.llm.llmentor.cleaner.DocumentCleaner;
import cn.learn.llm.llmentor.embedding.EmbeddingService;
import cn.learn.llm.llmentor.es.ElasticSearchService;
import cn.learn.llm.llmentor.es.EsDocumentChunk;
import cn.learn.llm.llmentor.reader.DocumentReaderFactory;
import cn.learn.llm.llmentor.splitter.OverlapParagraphTextSplitter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * es 相关控制器
 *
 * @author lianglei
 * @version 1.0
 * @date 2026/4/26 19:29
 */
@RestController
@RequestMapping("/rag/es")
public class RagEsController {

    @Autowired
    private DocumentReaderFactory readerFactory;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private ElasticSearchService elasticSearchService;

    /**
     * 写入ES
     *
     * @param filePath
     * @return
     * @throws Exception
     */
    @RequestMapping("/write")
    public String write(@RequestParam("filePath") String filePath) throws Exception {
        File files = new File(filePath);
        if (!files.exists() || !files.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件: " + filePath);
        }
        //1、加载文档
        List<Document> documents = readerFactory.read(files);

        //2、文档清洗
        documents = DocumentCleaner.cleanDocuments(documents);

        //3、文档分片
        OverlapParagraphTextSplitter splitter = new OverlapParagraphTextSplitter(
                // 每块最大字符数
                400,
                // 块之间重叠 100 字符
                100
        );
        List<Document> apply = splitter.split(documents);

        //4、存储到ES
        List<EsDocumentChunk> esDocs = apply.stream().map(doc -> {
            EsDocumentChunk chunk = new EsDocumentChunk();
            chunk.setId(doc.getId());
            chunk.setContent(doc.getText());
            chunk.setMetadata(doc.getMetadata());
            return chunk;
        }).collect(Collectors.toList());

        //5、写入es，批量
        elasticSearchService.bulkIndex(esDocs);

        //6、计算向量并保存
        embeddingService.embedAndSave(apply);
        return "success";
    }

    /**
     * 从es检索
     *
     * @param keyword
     * @return
     * @throws Exception
     */
    @RequestMapping("/search")
    public List<EsDocumentChunk> search(@RequestParam("keyword") String keyword) throws Exception {
        return elasticSearchService.searchByKeyword(keyword);
    }
}
