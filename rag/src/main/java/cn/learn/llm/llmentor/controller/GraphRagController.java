package cn.learn.llm.llmentor.controller;

import cn.learn.llm.llmentor.model.Director;
import cn.learn.llm.llmentor.model.Movie;
import cn.learn.llm.llmentor.service.GraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/27 11:21
 */
@Slf4j
@RestController
@RequestMapping("/rag/graph")
public class GraphRagController {

    @Autowired
    private Neo4jTemplate neo4jTemplate;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private GraphService graphService;

    @GetMapping("/ask")
    public String ask(@RequestParam("movieName") String movieName) {
        String context = graphService.retrieveContext(movieName);

        String prompt = """
                你是一个电影知识助手，请根据以下上下文回答问题。
                如果上下文没有足够信息，请回答“我不知道”。
                上下文会给出用户提供电影名执导的导演，并且还会列出该导演执导的其他电影。
                请结合上下文给出详细答案。
                
                要求：1、去掉用户输入的电影名，只回答导演和电影名。
                     2、回答简洁明了，不要包含额外的解释。
                     
                上下文：
                %s
                
                问题：%s
                回答：
                """.formatted(context, movieName + "的导演还执导过哪些电影？");

        return chatModel.call(prompt);
    }


    @GetMapping("/init")
    public String initData() {
        // 保存节点
        neo4jTemplate.save(new Director("张艺谋"));
        neo4jTemplate.save(new Director("陈思诚"));
        neo4jTemplate.save(new Movie("十面埋伏", 2004));
        neo4jTemplate.save(new Movie("影", 2016));
        neo4jTemplate.save(new Movie("英雄", 2002));
        neo4jTemplate.save(new Movie("误杀", 2019));
        neo4jTemplate.save(new Movie("唐人街探案二", 2016));
        neo4jTemplate.save(new Movie("唐人街探案三", 2018));

        neo4jClient.query("""
                        MATCH (p:Director {name: $name}), (m:Movie {title: $title})
                        MERGE (p)-[:DIRECTED]->(m)
                        """)
                .bind("张艺谋").to("name")
                .bind("十面埋伏").to("title")
                .run();

        neo4jClient.query("""
                        MATCH (p:Director {name: $name}), (m:Movie {title: $title})
                        MERGE (p)-[:DIRECTED]->(m)
                        """)
                .bind("张艺谋").to("name")
                .bind("影").to("title")
                .run();

        neo4jClient.query("""
                        MATCH (p:Director {name: $name}), (m:Movie {title: $title})
                        MERGE (p)-[:DIRECTED]->(m)
                        """)
                .bind("张艺谋").to("name")
                .bind("英雄").to("title")
                .run();

        neo4jClient.query("""
                        MATCH (p:Director {name: $name}), (m:Movie {title: $title})
                        MERGE (p)-[:DIRECTED]->(m)
                        """)
                .bind("陈思诚").to("name")
                .bind("误杀").to("title")
                .run();

        neo4jClient.query("""
                        MATCH (p:Director {name: $name}), (m:Movie {title: $title})
                        MERGE (p)-[:DIRECTED]->(m)
                        """)
                .bind("陈思诚").to("name")
                .bind("唐人街探案二").to("title")
                .run();

        neo4jClient.query("""
                        MATCH (p:Director {name: $name}), (m:Movie {title: $title})
                        MERGE (p)-[:DIRECTED]->(m)
                        """)
                .bind("陈思诚").to("name")
                .bind("唐人街探案三").to("title")
                .run();

        return "Data initialized successfully";
    }
}
