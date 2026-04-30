package cn.learn.llm.llmentor.langchain4j.controller;

import cn.learn.llm.llmentor.langchain4j.service.TravelPlanningAiService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 智能旅行规划控制器
 *
 * @author lianglei
 * @version 1.0
 * @date 2026/4/26 14:36
 */
@RestController
@RequestMapping("/travelPlan")
public class SmartTravelPlanningController {

    @Autowired
    private TravelPlanningAiService travelPlanningAiService;

    /**
     * 开启对话
     *
     * @param response
     * @return
     */
    @RequestMapping("/start")
    public Map<String, String> startTravelPlanning(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");

        String memoryId = UUID.randomUUID().toString();

        String welcomeMessage = """
                🌟 欢迎使用智能行程规划助手！
                
                我可以帮助您制定个性化的旅行计划。为了给您提供最佳的建议，我需要了解一些基本信息：
                
                • 您想去哪里旅行？
                • 计划什么时候出发？
                • 大概的预算范围？
                • 和谁一起旅行？
                • 您的兴趣爱好？
                
                请告诉我您的旅行想法，我会根据您提供的信息逐步完善行程计划！
                """;

        return Map.of(
                "sessionId", memoryId,
                "message", welcomeMessage
        );
    }

    /**
     * 对话，可能会要求需求澄清或者给出行程建议
     *
     * @param response
     * @param memoryId
     * @param message
     * @return
     */
    @RequestMapping("/chat")
    public String chatWithPlanner(HttpServletResponse response,
                                  @RequestParam String memoryId,
                                  @RequestParam String message) {
        response.setCharacterEncoding("UTF-8");

        return travelPlanningAiService.chatWithTraveler(memoryId, message);
    }

    /**
     * 强制生成旅行建议
     *
     * @param response
     * @param memoryId
     * @return
     */
    @RequestMapping("/forcePlan")
    public String forcePlan(HttpServletResponse response, @RequestParam String memoryId) {
        response.setCharacterEncoding("UTF-8");

        return travelPlanningAiService.chatWithTraveler(memoryId,
                "请基于我们到目前为止的所有对话，生成完整详细的行程规划方案");
    }
}
