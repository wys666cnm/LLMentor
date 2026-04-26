package cn.learn.llm.llmentor.controller;

import cn.learn.llm.llmentor.cleaner.DocumentCleaner;
import cn.learn.llm.llmentor.reader.DocumentReaderFactory;
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
 * 文件读取控制器
 *
 * @author lianglei
 * @version 1.0
 * @date 2026/4/22 17:53
 */
@RestController
@RequestMapping("/rag")
public class RagReaderController {

    @Autowired
    private DocumentReaderFactory documentReaderFactory;


    @GetMapping("/read")
    public List<Document> readDocument(@RequestParam("path") String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件: " + path);
        }
        try {
            return documentReaderFactory.read(file);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }


    @GetMapping("/readAndClean")
    public List<Document> readAndCleanDocument(@RequestParam("path") String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件: " + path);
        }
        try {

            List<Document> documents = documentReaderFactory.read(file);

            return DocumentCleaner.cleanDocuments(documents);

        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }

}
