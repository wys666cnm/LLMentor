package cn.learn.llm.llmentor.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/22 17:26
 */
@Service
public class MarkdownReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".markdown");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        Resource resource = new FileSystemResource(file);

        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                // 水平线分割生成新文档
                .withHorizontalRuleCreateDocument(false)
                // 不包含代码块
                .withIncludeCodeBlock(false)
                // 不包含引用
                .withIncludeBlockquote(false)
                // 添加文件名元数据
                .withAdditionalMetadata("filename", file.getName())
                .build();
        MarkdownDocumentReader markdownReader = new MarkdownDocumentReader(resource, config);
        return markdownReader.get();
    }
}
