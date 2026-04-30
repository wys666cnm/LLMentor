package cn.learn.llm.llmentor.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/27 10:41
 */
@Node("Movie")
public class Movie {

    @Id
    private String title;

    private int year;

    public Movie() {
    }

    public Movie(String title, int year) {
        this.title = title;
        this.year = year;
    }

    // Getters
    public String getTitle() {
        return title;
    }

    public int getYear() {
        return year;
    }
}
