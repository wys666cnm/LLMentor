package cn.learn.llm.llmentor.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;


/**
 * @author lianglei
 * @version 1.0
 * @date 2026/5/5 20:05
 */
@Slf4j
public class SimpleRecActAgent {

    private static final String REACT_SYSTEM_PROMPT = """
            # 角色
            你是一个严格的基于React架构（Reasoning-Act-Observation）的智能助手，会通过Reasoning -> Act(ToolCall) -> Observation
            的反复循环来逐步解决任务  ，你擅长使用工具帮我解决问题。
            
            你的工作流程是（极其重要）：
            思考：基于当前获得的信息进行推理和反思，明确下一步行动的目标。
            行动：用于表示需要调用的工具，每一步行动必须是以下两种之一：
              1、工具调用 [Function Calling]：根据任务需要，确定调用工具。
              2、Finish[答案]：得出明确答案后使用此操作，返回答案并终止任务。
            观察：记录前一步行动的结果。
            
            你可以进行多轮推理和检索，但必须严格按照上述格式进行操作，尤其是每一步“行动”只能使用上述两种类型之一。
            """;

    private final String name;

    private final ChatMemory chatMemory;

    private final ChatModel chatModel;

    private final List<ToolCallback> tools;

    private ChatClient chatClient;

    private int maxRounds;

    public SimpleRecActAgent(String name, ChatMemory chatMemory, ChatModel chatModel, List<ToolCallback> tools, int maxRounds) {
        this.name = name;
        this.chatMemory = chatMemory;
        this.chatModel = chatModel;
        this.tools = tools;
        this.maxRounds = maxRounds;
        // 初始化ChatClient
        initChatClient();

        // 检查ChatClient是否初始化成功
        if (this.chatClient == null) {
            throw new IllegalArgumentException("ChatClient 初始化失败！");
        }
    }

    public String call(String question) {
        return callInternal(null, question);
    }

    public String call(String conversationId, String question) {
        return callInternal(conversationId, question);
    }

    private String callInternal(String conversationId, String question) {
        

        return "";
    }


    public void initChatClient() {
        try {
            log.info("初始化 ChatClient...");

            ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(tools)
                    .internalToolExecutionEnabled(false)
                    .build();

            this.chatClient = ChatClient.builder(chatModel)
                    .defaultOptions(chatOptions)
                    .defaultToolCallbacks(tools)
                    .build();
        } catch (Exception e) {
            log.error("ChatClient 初始化失败：" + e.getMessage(), e);
            throw new RuntimeException("ChatClient 初始化失败：" + e.getMessage(), e);
        }
    }
}
