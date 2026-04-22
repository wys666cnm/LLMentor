package cn.learn.llm.llmentor.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/22 17:47
 */
@Service
public class TikaReaderStrategy implements DocumentReaderStrategy {
    @Override
    public boolean supports(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".docx") || name.endsWith(".doc");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        Resource resource = new FileSystemResource(file);
        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
        return tikaDocumentReader.get();
    }
}
