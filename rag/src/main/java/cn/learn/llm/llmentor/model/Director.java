package cn.learn.llm.llmentor.model;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/27 10:53
 */
@Node("Director")
public class Director {

    //导演
    private String director;
    @Id
    private String name;

    public Director() {
    }

    public Director(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
