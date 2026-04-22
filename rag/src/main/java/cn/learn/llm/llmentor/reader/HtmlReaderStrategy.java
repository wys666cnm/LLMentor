package cn.learn.llm.llmentor.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/22 17:17
 */
@Service
public class HtmlReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".html") || name.endsWith(".htm");
    }


    @Override
    public List<Document> read(File file) throws IOException {
        Resource resource = new FileSystemResource(file);

        JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                .selector("p") // 只提取p标签段落
                .charset("UTF-8") // 文件编码
                .includeLinkUrls(true) // 包含超链接
                .metadataTags(List.of("author", "date")) // 提取meta标签的元数据
                .additionalMetadata("filename", file.getName()) // 添加自定义元数据
                .build();

        JsoupDocumentReader jsoupReader = new JsoupDocumentReader(resource, config);

        return jsoupReader.get();
    }
}
