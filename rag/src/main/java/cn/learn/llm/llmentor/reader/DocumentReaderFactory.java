package cn.learn.llm.llmentor.reader;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/22 17:50
 */
@Component
public class DocumentReaderFactory {

    @Autowired
    private List<DocumentReaderStrategy> strategy;

    public List<Document> read(File file) throws IOException {
        for (DocumentReaderStrategy reader : strategy) {
            if (reader.supports(file)) {
                return reader.read(file);
            }
        }
        throw new IllegalArgumentException("不支持的文件类型");
    }

}
