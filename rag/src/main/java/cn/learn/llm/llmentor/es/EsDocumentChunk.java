package cn.learn.llm.llmentor.es;

import lombok.Data;

import java.util.Map;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/26 19:22
 */
@Data
public class EsDocumentChunk {

    private String id;

    private String content;

    private Map<String, Object> metadata;
}
