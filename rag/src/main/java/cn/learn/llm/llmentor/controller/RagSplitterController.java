package cn.learn.llm.llmentor.controller;

import cn.learn.llm.llmentor.cleaner.DocumentCleaner;
import cn.learn.llm.llmentor.reader.DocumentReaderFactory;
import cn.learn.llm.llmentor.splitter.OverlapParagraphTextSplitter;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/22 20:20
 */
@RestController
@RequestMapping("/rag")
public class RagSplitterController {


    @Autowired
    private DocumentReaderFactory documentReaderFactory;

    @GetMapping("/split")
    public List<Document> splitDocument(@RequestParam("path") String path) {
        List<Document> documents = null;
        try {
            documents = documentReaderFactory.read(new File(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (Document document : documents) {
            System.out.println("before chunk : " + document.getText());
            System.out.println("");
            OverlapParagraphTextSplitter tokenTextSplitter = new OverlapParagraphTextSplitter(
                    100,
                    5);

            List<Document> chunkedDocuments = tokenTextSplitter.split(document);
            for (Document chunkedDocument : chunkedDocuments) {
                System.out.println("after chunk : " + chunkedDocument.getText());
                System.out.println("");
            }
            System.out.println("==============");
        }
        return documents;
    }

    @GetMapping("/readAndSplit")
    public List<Document> readAndSplit(@RequestParam("path") String path) {
        File files = new File(path);
        if (!files.exists() || !files.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件: " + path);
        }

        try {
            //1、文档读取
            List<Document> documents = documentReaderFactory.read(files);

            //2、文档清洗
            documents = DocumentCleaner.cleanDocuments(documents);

            //3、文档分块
//            documents = split(documents); //token分割
            OverlapParagraphTextSplitter splitter = new OverlapParagraphTextSplitter(
                    // 每块最大字符数
                    400,
                    // 块之间重叠 100 字符
                    100
            );
            documents = splitter.split(documents);
            return documents;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 文档分割
     *
     * @param documents
     * @return
     */
    private List<Document> split(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }

        TokenTextSplitter splitter = new TokenTextSplitter(
                // 每块最多 600 tokens
                600,
                // 每块至少 400 字符再考虑断点
                300,
                // 太短的不做嵌入
                5,
                // 最多拆分8000块
                8000,
                // 保留句号、换行符
                true
        );

        return splitter.apply(documents);
    }
}
