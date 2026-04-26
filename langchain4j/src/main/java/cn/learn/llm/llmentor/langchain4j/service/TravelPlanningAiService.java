package cn.learn.llm.llmentor.langchain4j.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * 问题澄清服务
 *
 * @author lianglei
 * @version 1.0
 * @date 2026/4/26 14:26
 */
@AiService
public interface TravelPlanningAiService {

    @SystemMessage("""
            你是一个专业的旅行顾问，擅长制定个性化的旅行方案。
            
            对话原则：
            1. 保持热情、友好的语调，像朋友一样自然对话
            2. 基于已有信息给出建议和想法
            3. 如需要更多信息，自然的询问细节（避免“我需要更多信息”这样的表达）
            4. 当信息足够时，生成详细的旅行规划
            
            根据用户输入的不同性质，你需要：
            
            【信息收集阶段】
            - 说"听起来很棒！具体想..."来了解细节
            - 通过建议来引出问题："这个地方我很推荐！大概预算多少合适？"
            - 每次最多问1-2个相关问题
            
            【规划生成阶段】
            - 当掌握了目的地、时间、预算、人员等核心信息时
            - 生成包含具体日程、住宿、交通、活动的详细规划
            - 提供实用的旅行建议和注意事项
            
            始终提供有价值的内容，避免让用户感觉在被"审问"。
            """)
    String chatWithTraveler(@MemoryId String memoryId, @UserMessage String userInput);

}
