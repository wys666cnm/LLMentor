package cn.learn.llm.llmentor.controller;

import cn.learn.llm.llmentor.router.GraphDatabaseService;
import cn.learn.llm.llmentor.router.QueryRouteService;
import cn.learn.llm.llmentor.router.RelationalDatabaseService;
import cn.learn.llm.llmentor.router.VectorDatabaseService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/25 21:39
 */
@RestController
@RequestMapping("/rag/router")
public class RagRouterController {

    @Autowired
    private QueryRouteService queryRouteService;

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    @Autowired
    private GraphDatabaseService graphDatabaseService;

    @Autowired
    private RelationalDatabaseService relationalDatabaseService;

    @GetMapping("/route")
    public String route(String query) {
        return queryRouteService.route(query);
    }

    @RequestMapping("/routeQuery")
    public String ragQuery(HttpServletResponse response, @RequestParam String question) {
        response.setCharacterEncoding("UTF-8");

        String databaseType = queryRouteService.route(question);

        String result;
        switch (databaseType.trim()) {
            case "VECTOR":
                result = vectorDatabaseService.searchVectorDatabase(question);
                break;
            case "GRAPH":
                result = graphDatabaseService.searchGraphDatabase(question);
                break;
            case "RELATIONAL":
                result = relationalDatabaseService.searchRelationalDatabase(question);
                break;
            default:
                result = "无法确定合适的数据库类型，默认使用向量数据库: " +
                        vectorDatabaseService.searchVectorDatabase(question);
        }

        return String.format("路由到: %s 数据库\n\n查询结果:\n%s", databaseType, result);
    }
}
