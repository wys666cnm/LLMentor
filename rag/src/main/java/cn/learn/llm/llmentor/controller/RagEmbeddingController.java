package cn.learn.llm.llmentor.controller;

import cn.learn.llm.llmentor.cleaner.DocumentCleaner;
import cn.learn.llm.llmentor.embedding.EmbeddingService;
import cn.learn.llm.llmentor.reader.DocumentReaderFactory;
import cn.learn.llm.llmentor.splitter.OverlapParagraphTextSplitter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/24 20:37
 */
@RestController
@RequestMapping("/rag/embedding")
public class RagEmbeddingController {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private DocumentReaderFactory documentReaderFactory;

    @GetMapping("/embed")
    public String embed(@RequestParam("filePath") String filePath) {
        File files = new File(filePath);
        if (!files.exists() || !files.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件: " + filePath);
        }
        //1、根据文件路径读取文档
        List<Document> documents = null;
        try {
            documents = documentReaderFactory.read(files);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //2、文档清洗
        documents = DocumentCleaner.cleanDocuments(documents);

        //3、文档分块
        OverlapParagraphTextSplitter splitter = new OverlapParagraphTextSplitter(
                // 每块最大字符数
                400,
                // 块之间重叠 100 字符
                100
        );
        documents = splitter.split(documents);

        //4、向量化并存储
        embeddingService.embedAndSave(documents);

        //简洁写法
//        List<Document> allChunkedDocuments = documents.stream()
//                .flatMap(document -> {
//                    RecursiveCharacterTextSplitter splitters = new RecursiveCharacterTextSplitter(300, new String[]{"\n\n", "\n"});
//                    return splitters.split(document).stream();
//                })
//                .collect(Collectors.toList());
//        embeddingService.embedAndSave(allChunkedDocuments);

        return "success";
    }
}
