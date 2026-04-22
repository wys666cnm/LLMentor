package cn.learn.llm.llmentor.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.print.Doc;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/22 17:33
 */
@Service
public class JsonReaderStrategy implements DocumentReaderStrategy {

    @Override
    public boolean supports(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".json");
    }

    @Override
    public List<Document> read(File file) throws IOException {
        Resource resource = new FileSystemResource(file);
        // 假设目标提取json的两个字段description和content
        JsonReader jsonReader = new JsonReader(resource, "description", "content");
        return jsonReader.get();
    }
}
