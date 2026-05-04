package cn.learn.llm.llmentor.agent;

// ===================== 第三方工具/服务导入 =====================
import cn.learn.llm.llmentor.config.ChatModelConfig;      // ChatModel 配置工厂，用于 main 方法快速创建模型
import cn.learn.llm.llmentor.tools.SearchService;         // 搜索工具（示例）
import cn.learn.llm.llmentor.tools.WeatherService;        // 天气工具（示例）
import io.modelcontextprotocol.client.McpClient;               // MCP 客户端接口（本文件未直接使用，预留扩展）
import io.modelcontextprotocol.client.McpSyncClient;           // MCP 同步客户端（本文件未直接使用，预留扩展）
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport; // MCP HTTP 传输层（预留扩展）
import lombok.extern.slf4j.Slf4j;                               // Lombok 日志注解，自动生成 log 对象

// ===================== Spring AI 核心导入 =====================
import org.springframework.ai.chat.client.ChatClient;           // Spring AI 聊天客户端，封装与大模型的交互
import org.springframework.ai.chat.client.ChatClientResponse;   // ChatClient 的响应包装，含 ChatResponse + 上下文
import org.springframework.ai.chat.client.advisor.api.Advisor;  // 顾问接口，用于在调用前后插入拦截逻辑（如反思）
import org.springframework.ai.chat.memory.ChatMemory;           // 对话记忆抽象接口
import org.springframework.ai.chat.memory.MessageWindowChatMemory; // 基于滑动窗口的对话记忆实现
import org.springframework.ai.chat.messages.*;                  // 消息类型：SystemMessage, UserMessage, AssistantMessage, ToolResponseMessage 等
import org.springframework.ai.chat.model.ChatModel;             // 大模型接口抽象
import org.springframework.ai.chat.model.ChatResponse;          // 大模型响应，包含 Generation 列表
import org.springframework.ai.chat.model.Generation;            // 单次生成结果，含 output(AssistantMessage)
import org.springframework.ai.model.tool.ToolCallingChatOptions; // 工具调用选项，控制工具行为
import org.springframework.ai.support.ToolCallbacks;           // 工具回调工厂，从 @Tool 注解方法创建 ToolCallback
import org.springframework.ai.tool.ToolCallback;               // 工具回调接口，封装工具定义 + 执行逻辑
import org.springframework.util.CollectionUtils;               // Spring 集合工具类

// ===================== 响应式编程导入 =====================
import reactor.core.publisher.Flux;        // 响应式流，用于流式输出
import reactor.core.publisher.Sinks;       // 响应式数据槽，手动推送数据到 Flux
import reactor.core.scheduler.Schedulers;  // 响应式调度器，控制线程模型

// ===================== Java 标准库导入 =====================
import java.util.*;                               // 集合框架
import java.util.concurrent.atomic.AtomicBoolean;  // 原子布尔，线程安全的状态标记
import java.util.concurrent.atomic.AtomicInteger;   // 原子整数，线程安全的计数器
import java.util.concurrent.atomic.AtomicLong;      // 原子长整型，线程安全的轮次计数器

@Slf4j
public class SimpleReactAgent {

    public static final String REACT_AGENT_SYSTEM_PROMPT = """
            ## 角色
            你是一个严格遵循 ReAct 模式的智能 AI 助手，会通过 Reasoning → Act(ToolCall) → Observation 的反复循环来逐步解决任务。

            ## 工具调用规则（极其重要）
            1. 如果需要调用工具：必须使用 OpenAI 官方 ToolCall 结构，并且 **只能通过工具调用字段输出**。
            2. 工具调用时：**禁止在 content 中出现任何形式的工具调用文本**（包括 JSON、<tool_call>、函数名、参数、思考、推理或描述）。
            3. 工具调用消息必须是一次性、原子性输出，不得混杂任何解释或内容。
            4. 工具调用前后不得输出任何多余文字、标签、换行、推理轨迹或说明。
            5. 调用工具时：
               -工具参数必须是有效的JSON
               -参数必须简洁，不超过500个字符
               -切勿包含以前的工具结果、原始内容、HTML或长文本
               -仅包括工具所需的最小控制参数

            ## 工具执行结果
            系统会自动将工具执行结果作为 ToolResponseMessage 注入上下文，你只需读取并决定下一步动作。

            ## 最终答案规则
            1. 如果上下文已经拥有了完成任务的全部信息，则不要再调用任何工具。
            2. 在这种情况下，你必须输出最终自然语言答案，且 **禁止包含任何工具调用格式**。
            3. 最终答案只允许是自然语言，不能包含 JSON、思考过程、reasoning、ToolCall 或伪代码。

            ## 强制要求（必须遵守）
            1. 工具调用消息必须只通过 ToolCall 字段输出，不允许在 content 字段体现工具调用迹象。
            2. 如果本轮没有工具调用，则视为任务完成，你必须输出最终答案。
            3. 不允许重复调用同一个工具（名称 + 参数完全一致），除非工具调用失败。
            4. 禁止输出会干扰工具系统解析的任何结构（如 <reason>、<ToolCall>、函数 JSON、或模型内部思考）。
            5. 如果上下文已经包含了完成任务的全部信息，则不要再调用任何工具。
            """;

    // ===================== 核心字段 =====================

    /** Agent 名称，用于标识和日志 */
    private final String name;

    /**
     * 大语言模型实例（ChatModel）
     * 【原理】ChatModel 是 Spring AI 对大模型的抽象，封装了 API 调用、参数配置等细节。
     * 它是 Agent 的"大脑"，负责理解指令、生成文本、决定是否调用工具。
     */
    private final ChatModel chatModel;

    /**
     * 可用工具列表（ToolCallback）
     * 【原理】ToolCallback = 工具定义(ToolDefinition) + 工具执行逻辑(call方法)。
     * 大模型只能"看到"工具定义（名称、描述、参数 schema），但不会直接执行工具；
     * 执行由 Agent 代码手动完成，结果再回传给模型。
     */
    private final List<ToolCallback> tools;

    /**
     * 用户自定义系统提示词（业务相关的角色设定）
     * 会与 REACT_AGENT_SYSTEM_PROMPT 一起作为系统消息注入
     */
    private final String systemPrompt;

    /**
     * ChatClient — Spring AI 的聊天客户端
     * 【原理】ChatClient 是对 ChatModel 的高层封装，提供了流式 API（prompt().call()/stream()），
     * 并集成了工具回调、Advisor 等功能。本 Agent 通过它与大模型交互。
     */
    private ChatClient chatClient;

    /**
     * 最大推理轮次
     * 【原理】防止 Agent 无限循环（模型可能反复调用工具无法收敛）。
     * 每一轮 = 一次模型调用 + 可选的工具执行。达到上限后强制输出最终答案。
     */
    private int maxRounds;

    /**
     * 对话记忆（ChatMemory）
     * 【原理】ChatMemory 存储历史对话消息，使 Agent 具备多轮对话能力。
     * 每次调用时加载历史消息，结束后保存新的用户消息和助手回复。
     * conversationId 用于区分不同的会话。
     */
    private ChatMemory chatMemory;

    // ===================== 反思(Reflection)相关字段 =====================

    /**
     * 顾问（Advisor）列表 — 功能增强拦截器
     * 【原理】Advisor 是 Spring AI 的拦截器模式，可以在 ChatClient 调用前后插入自定义逻辑。
     * 在本 Agent 中，Advisor 主要用于实现"反思机制"：
     *   - 调用前：无特殊处理
     *   - 调用后：检查模型输出质量，判断是否需要反思修正
     *   - 如果需要反思，在上下文(context)中设置 reflection.required=true 和 reflection.feedback
     */
    private List<Advisor> advisors;

    /**
     * 最大反思轮数
     * 【原理】反思机制允许 Agent 对自己的输出进行自我审视和修正。
     * 但反思本身也可能无限循环，因此需要限制轮次。
     * 当反思轮次达到上限时，直接输出当前结论。
     */
    private int maxReflectionRounds;

    /**
     * 全参构造函数
     * 【设计模式】构造函数只做赋值和初始化，不执行业务逻辑，保证对象创建的安全性。
     * 所有的 Agent 都必须通过 Builder 创建，确保参数的完整性和一致性。
     *
     * @param name               Agent 名称
     * @param chatModel          大模型实例（Agent 的"大脑"）
     * @param tools              可用工具列表
     * @param systemPrompt       业务系统提示词
     * @param maxRounds          最大推理轮次（0 表示不限制）
     * @param chatMemory         对话记忆（null 表示不使用记忆）
     * @param advisors           顾问/拦截器列表（null 表示不使用）
     * @param maxReflectionRounds 最大反思轮次（0 表示不使用反思机制）
     */
    public SimpleReactAgent(String name, ChatModel chatModel, List<ToolCallback> tools, String systemPrompt, int maxRounds, ChatMemory chatMemory, List<Advisor> advisors, int maxReflectionRounds) {
        this.name = name;                       // 赋值 Agent 名称
        this.chatModel = chatModel;             // 赋值大模型实例
        this.tools = tools;                     // 赋值工具列表
        this.systemPrompt = systemPrompt;       // 赋值系统提示词
        this.maxRounds = maxRounds;             // 赋值最大推理轮次
        this.chatMemory = chatMemory;           // 赋值对话记忆

        // 反思相关参数赋值
        this.maxReflectionRounds = maxReflectionRounds;  // 赋值最大反思轮次
        this.advisors = advisors;               // 赋值顾问/拦截器列表

        // 初始化 ChatClient（核心组件，必须成功）
        initChatClient();

        // 安全检查：如果 ChatClient 初始化失败，立即抛出异常
        // 这是一种 Fail-Fast 策略，避免 Agent 在运行时才发现不可用
        if (this.chatClient == null) {
            throw new IllegalStateException("ChatClient 初始化失败！");
        }
    }

    /**
     * 初始化 ChatClient
     *
     * 【原理】这是 Agent 最核心的初始化步骤，做了两件关键的事：
     *
     * 1. 设置 internalToolExecutionEnabled = false
     *    默认情况下，Spring AI 会在模型返回 ToolCall 时自动执行工具并将结果喂回模型，
     *    形成一个透明的自动循环。但在 ReAct Agent 中，我们需要手动控制这个流程：
     *    - 手动执行工具（可以加入日志、错误处理、超时控制等）
     *    - 手动管理消息列表（确保每轮的消息顺序正确）
     *    - 手动实现轮次计数和终止条件判断
     *
     * 2. 注册 ToolCallbacks
     *    将工具定义注册到 ChatClient，使得大模型能够"看到"可用工具的名称、描述和参数 schema。
     *    模型根据这些信息决定是否调用工具，但实际执行由 Agent 代码完成。
     *
     * 3. 注册 Advisors
     *    将顾问（拦截器）注册到 ChatClient，它们会在每次模型调用前后执行自定义逻辑。
     *    例如：反思 Advisor 会在模型返回后检查输出质量。
     */
    private void initChatClient() {
        try {
            // 构建工具调用选项：
            // - toolCallbacks(tools)：注册所有工具，让模型知道有哪些工具可用
            // - internalToolExecutionEnabled(false)：关闭自动工具执行，由 Agent 手动控制
            //   【关键】这是 ReAct Agent 能手动控制推理循环的前提
            ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(tools)
                    .internalToolExecutionEnabled(false)
                    .build();

            // 构建 ChatClient
            ChatClient.Builder builder = ChatClient.builder(chatModel);

            // 如果配置了 Advisor（反思拦截器等），注册为默认 Advisor
            // Advisor 会在每次 ChatClient 调用时自动执行
            if (!CollectionUtils.isEmpty(advisors)) {
                builder.defaultAdvisors(advisors);
            }

            // 构建最终的 ChatClient：
            // - defaultOptions(toolOptions)：设置工具调用选项
            // - defaultToolCallbacks(tools)：注册默认工具回调（确保工具定义和执行逻辑都可用）
            this.chatClient = builder.defaultOptions(toolOptions).defaultToolCallbacks(tools).build();
        } catch (Exception e) {
            // 初始化失败时包装为 RuntimeException 抛出，防止 Agent 在不可用状态下运行
            throw new RuntimeException("ChatClient 初始化失败：" + e.getMessage(), e);
        }
    }

    /**
     * 非流式调用（无会话记忆）
     *
     * 【原理】无状态的单次问答，每次调用都是独立的，不保留历史对话。
     * 适用于简单的、不需要上下文延续的场景。
     *
     * @param question 用户问题
     * @return Agent 的最终答案
     */
    public String call(String question) {
        // conversationId 传 null，表示不使用会话记忆
        return callInternal(null, question);
    }

    /**
     * 非流式调用（带会话记忆）
     *
     * 【原理】通过 conversationId 关联对话历史，实现多轮对话。
     * 同一个 conversationId 的多次调用会共享对话上下文。
     *
     * @param conversationId 会话 ID，用于区分不同的对话
     * @param question       用户问题
     * @return Agent 的最终答案
     */
    public String call(String conversationId, String question) {
        return callInternal(conversationId, question);
    }

    /**
     * 非流式调用的核心实现
     *
     * 【ReAct 循环原理】
     * 这是最核心的方法，实现了 ReAct 的完整循环：
     *
     * ┌──────────────────────────────────────┐
     * │ 1. 构建消息列表（系统提示 + 历史 + 用户问题）│
     * │ 2. 调用大模型                          │
     * │ 3. 判断模型响应：                        │
     * │    ├─ 无工具调用 → 检查是否需要反思        │
     * │    │    ├─ 需要反思 → 注入反思反馈，继续循环 │
     * │    │    └─ 不需要 → 返回最终答案           │
     * │    └─ 有工具调用 → 执行工具，将结果加入消息  │
     * │ 4. 回到步骤 2                           │
     * └──────────────────────────────────────┘
     *
     * 【消息列表结构】
     * 消息列表是 OpenAI API 的核心概念，每次请求都会发送完整的消息历史：
     * [SystemMessage(ReAct规则)] → [SystemMessage(业务提示)] → [历史消息...] → [UserMessage(当前问题)]
     * → [AssistantMessage(模型回复/工具调用)] → [ToolResponseMessage(工具结果)] → ...
     *
     * @param conversationId 会话 ID（null 表示不使用记忆）
     * @param question       用户问题
     * @return 最终答案
     */
    public String callInternal(String conversationId, String question) {
        // 创建线程安全的消息列表
        // 【原理】synchronizedList 确保在并发场景下消息列表的线程安全性
        // 虽然非流式调用是同步的，但保持一致的线程安全策略是好习惯
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());

        // 判断是否使用会话记忆：需要同时满足 conversationId 非空 和 chatMemory 已配置
        boolean useMemory = conversationId != null && chatMemory != null;

        // ===== 注入系统提示词 =====
        // 【原理】SystemMessage 是最高优先级的指令，模型必须遵守。
        // 放在最前面，确保模型在处理任何用户输入前就已理解行为规则。
        messages.add(new SystemMessage(REACT_AGENT_SYSTEM_PROMPT));  // ReAct 行为规则
        messages.add(new SystemMessage(systemPrompt));                // 业务角色设定

        // ===== 加载历史记忆 =====
        // 【原理】将之前同一会话的对话历史注入消息列表，使模型具备上下文连续性。
        // 历史消息包含之前的 UserMessage 和 AssistantMessage，模型可以理解对话脉络。
        if (useMemory) {
            List<Message> history = chatMemory.get(conversationId);  // 从记忆存储中获取历史消息
            if (history != null && !history.isEmpty()) {
                messages.addAll(history);  // 将历史消息追加到系统提示之后
            }
        }

        // ===== 添加当前用户问题 =====
        // 【原理】用 <question> 标签包裹用户问题，帮助模型区分问题和上下文中的其他文本。
        // 这是一种 Prompt Engineering 技巧，减少模型混淆的可能性。
        messages.add(new UserMessage("<question>" + question + "</question>"));

        // ===== 保存用户问题到记忆 =====
        // 【注意】这里保存的是原始问题（不带 <question> 标签），
        // 因为记忆中的消息是给模型看的，标签只在当前请求中用于区分
        if (useMemory) {
            chatMemory.add(conversationId, new UserMessage(question));
        }

        // 推理轮次计数器
        int round = 0;

        // 反思轮次计数器
        int reflectionRound = 0;

        // ===================== ReAct 主循环 =====================
        // 【原理】这是一个无限循环，每轮代表一次 "推理→行动→观察" 的迭代。
        // 退出条件：1. 模型不再调用工具（输出最终答案）2. 达到最大轮次 3. 反思轮次用尽
        while (true) {
            round++;  // 轮次递增

            // ===== 轮次上限检查 =====
            // 【原理】防止 Agent 因模型反复调用工具而陷入无限循环。
            // maxRounds > 0 表示启用了轮次限制
            if (maxRounds > 0 && round > maxRounds) {
                log.warn("=== 达到 maxRounds（{}），强制生成最终答案 ===", maxRounds);

                // 注入强制终止提示，告诉模型必须直接输出答案
                messages.add(new UserMessage("""
                        你已达到最大推理轮次限制。
                        请基于当前已有的上下文信息，
                        直接给出最终答案。
                        禁止再调用任何工具。
                        如果信息不完整，请合理总结和说明。
                        """));

                // 调用模型生成最终答案（不带工具调用选项的约束，因为已禁止工具调用）
                String finalText = chatClient.prompt().messages(messages).call().content();

                // 保存最终答案到记忆
                if (useMemory) {
                    chatMemory.add(conversationId, new AssistantMessage(finalText));
                }
                return finalText;  // 返回最终答案
            }

            // ===== 调用大模型 =====
            // 【原理】将完整的消息列表发送给大模型，获取模型的响应。
            // chatClientResponse() 返回 ChatClientResponse，包含：
            //   - chatResponse：模型的标准响应（含生成文本和工具调用）
            //   - context：Advisor 执行后的上下文数据（如反思标记）
            ChatClientResponse chatResponse = chatClient
                    .prompt()
                    .messages(messages)   // 传入完整的消息历史
                    .call()               // 同步调用
                    .chatClientResponse(); // 获取包含上下文的完整响应

            // 提取模型输出的文本内容
            String aiText = chatResponse.chatResponse().getResult().getOutput().getText();

            // 构建 AssistantMessage 的 Builder（后续根据是否有工具调用来决定是否添加 toolCalls）
            // 【原理】AssistantMessage 需要同时包含文本内容和工具调用信息，
            // 这样模型在下一轮调用时才能理解上一轮的完整输出
            AssistantMessage.Builder builder = AssistantMessage.builder().content(aiText);

            // ===== 判断是否有工具调用 =====
            // 【核心判断】hasToolCalls() 是 ReAct 循环的关键分支点：
            // - 无工具调用 → 模型认为信息足够，准备输出最终答案
            // - 有工具调用 → 模型决定调用工具获取更多信息
            if (!chatResponse.chatResponse().hasToolCalls()) {

                // ===== 反思机制检查 =====
                // 【原理】即使模型没有调用工具（看似给出了最终答案），
                // 也可能需要反思。Advisor 会在 context 中标记 reflection.required=true
                // 表示当前答案质量不足，需要修正。
                // 条件：1. 启用了反思(maxReflectionRounds > 0) 2. Advisor 标记了需要反思
                if (maxReflectionRounds > 0 && Boolean.TRUE.equals(chatResponse.context().get("reflection.required"))) {

                    // 反思轮次上限检查
                    if (reflectionRound >= maxReflectionRounds) {
                        log.warn("======= Reflection 最大轮次已达，直接输出结论 =======");
                        // 保存当前答案到记忆并返回
                        if (useMemory) {
                            chatMemory.add(conversationId, new AssistantMessage(aiText));
                        }
                        return aiText;
                    }

                    reflectionRound++;  // 反思轮次递增
                    log.info("===== 当前反思机制，第 {} 轮次 =====", reflectionRound);

                    // 从 Advisor 上下文中提取反思反馈意见
                    String feedback = (String) chatResponse.context().get("reflection.feedback");

                    // 注入反思反馈到消息列表
                    // 【原理】将反思反馈作为 AssistantMessage 注入，模拟模型"自我审视"的过程。
                    // 这样模型在下一轮调用时会看到自己的输出和反思意见，从而改进答案。
                    // 注意：这里用 AssistantMessage 而非 UserMessage，是因为反思是模型自身的思考过程
                    messages.add(new AssistantMessage("""
                            【Reflection Feedback】
                            %s

                            请你根据以上反思意见重新规划任务，
                            必要时可以重新调用工具，
                            然后再给出最终答案。
                            """.formatted(feedback)));

                    // 继续下一轮 ReAct 循环（不返回，让 while 循环继续）
                    continue;
                }

                // ===== 无需反思，直接返回最终答案 =====
                // 保存到记忆并返回
                if (useMemory) {
                    chatMemory.add(conversationId, new AssistantMessage(aiText));
                }
                return aiText;  // 返回最终答案，ReAct 循环结束
            }

            // ===== 有工具调用：执行工具 =====

            // 将模型的 AssistantMessage（含文本和工具调用）加入消息列表
            // 【原理】根据 OpenAI API 规范，模型返回的 ToolCall 必须以 AssistantMessage 的形式
            // 出现在消息历史中，后续的 ToolResponseMessage 才能与之对应（通过 toolCallId 关联）
            messages.add(builder.toolCalls(chatResponse.chatResponse().getResult().getOutput().getToolCalls()).build());

            // 遍历所有工具调用，逐一执行
            // 【原理】模型可能一次返回多个工具调用（并行工具调用），需要逐一执行
            // 每个工具调用都有唯一的 id，用于将结果与调用关联
            chatResponse.chatResponse()
                    .getResult()
                    .getOutput()
                    .getToolCalls()
                    .forEach(toolCall -> {
                        String toolName = toolCall.name();       // 工具名称
                        String argsJson = toolCall.arguments();  // 工具参数（JSON 格式）

                        // 根据工具名称查找对应的 ToolCallback
                        ToolCallback callback = findTool(toolName);
                        if (callback == null) {
                            // 工具未找到，添加错误响应到消息列表
                            // 【原理】即使工具不存在，也必须返回 ToolResponseMessage，
                            // 否则模型会认为工具还在执行中，无法继续
                            addErrorToolResponse(messages, toolCall, "工具未找到：" + toolName);
                            return;  // 相当于 continue，处理下一个工具调用
                        }

                        // 执行工具
                        Object result;
                        try {
                            // 调用工具的 call 方法，传入 JSON 参数，获取执行结果
                            result = callback.call(argsJson);

                            // 构建工具响应消息
                            // 【原理】ToolResponseMessage 必须包含 toolCallId，
                            // 这样模型才能将响应与之前的工具调用关联起来
                            ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                                    toolCall.id(),   // 工具调用 ID，与 AssistantMessage 中的 ToolCall.id 对应
                                    toolName,        // 工具名称
                                    result.toString() // 工具执行结果（转为字符串）
                            );

                            // 将工具响应添加到消息列表
                            messages.add(ToolResponseMessage.builder().responses(List.of(tr)).build());
                        } catch (Exception ex) {
                            // 工具执行失败，添加错误响应
                            // 【原理】工具执行可能因网络、参数错误等原因失败，
                            // 将错误信息告知模型，让它决定下一步（重试或换策略）
                            addErrorToolResponse(messages, toolCall, "工具执行失败：" + ex.getMessage());
                        }
                    });
            // 工具执行完毕，while 循环继续，进入下一轮 ReAct 推理
        }
    }


    /**
     * 流式输出中每轮的运行模式枚举
     *
     * 【原理】在流式输出中，模型的响应是逐块(chunk)到达的，
     * 需要在处理过程中动态判断当前轮的模式：
     * - UNKNOWN：刚开始接收，尚未确定模式
     * - TOOL_CALL：检测到工具调用，本轮不输出文本给用户
     * - FINAL_ANSWER：没有工具调用，本轮输出的是最终答案（不再使用此枚举值，
     *                  而是通过 mode != TOOL_CALL 来判断）
     *
     * 为什么需要这个枚举？
     * 因为流式响应中，工具调用的参数是分多次 chunk 传过来的（流式拼接），
     * 需要一个状态标记来区分当前是在接收工具调用还是最终答案。
     */
    private enum RoundMode {
        UNKNOWN,       // 未知模式，还未接收到足够信息判断
        FINAL_ANSWER,  // 最终答案模式（本代码中实际未使用此值，用 mode != TOOL_CALL 代替）
        TOOL_CALL      // 工具调用模式，本轮不应将文本输出给用户
    }

    /**
     * 每轮执行的状态容器
     *
     * 【原理】在流式处理中，每轮的响应是分多个 chunk 逐步到达的，
     * 需要一个状态对象来累积和跟踪本轮的所有信息：
     *
     * - mode：当前轮的模式（工具调用 or 最终答案）
     * - textBuffer：累积文本内容（最终答案模式下，逐步拼接文本）
     * - toolCalls：累积工具调用列表（工具调用模式下，逐步拼接参数）
     *
     * 为什么用 synchronizedList？
     * 因为流式处理可能在不同线程中操作这些数据，需要保证线程安全。
     */
    private static class RoundState {
        RoundMode mode = RoundMode.UNKNOWN;  // 初始为未知模式，等待第一个 chunk 确定模式

        // 文本缓冲区，用于累积模型输出的文本内容
        // 在最终答案模式下，这些文本会被发送给用户
        StringBuilder textBuffer = new StringBuilder();

        // 工具调用列表，用于累积模型输出的工具调用
        // 同一个 toolCall 的参数可能跨多个 chunk 到达，需要通过 mergeToolCall 拼接
        List<AssistantMessage.ToolCall> toolCalls = Collections.synchronizedList(new ArrayList<>());
    }


    /**
     * 流式调用（无会话记忆）
     *
     * 【原理】流式输出使用 Reactor 的 Flux<String>，每产生一个文本片段就立即推送给调用者。
     * 用户体验更好，因为可以实时看到模型在"打字"，而不需要等待完整响应。
     *
     * 【Flux 基础】Flux 是 Reactor 的核心类型，表示一个异步序列：
     * - Flux.just("a", "b", "c")：同步创建
     * - Flux.create(sink -> ...)：手动推送
     * - sink.tryEmitNext(text)：推送一个元素
     * - sink.tryEmitComplete()：标记序列结束
     * - sink.tryEmitError(err)：标记序列出错
     *
     * @param question 用户问题
     * @return Flux<String> 流式文本序列
     */
    public Flux<String> stream(String question) {
        return streamInternal(null, question);  // conversationId 为 null，不使用记忆
    }

    /**
     * 流式调用（带会话记忆）
     *
     * @param conversationId 会话 ID
     * @param question       用户问题
     * @return Flux<String> 流式文本序列
     */
    public Flux<String> stream(String conversationId, String question) {
        return streamInternal(conversationId, question);
    }


    /**
     * 流式调用的核心实现
     *
     * 【流式 ReAct 的挑战】
     * 与同步调用不同，流式输出需要解决以下问题：
     * 1. 模型的响应是分 chunk 到达的，需要逐步判断是工具调用还是最终答案
     * 2. 工具调用的参数可能跨多个 chunk（流式拼接），需要累积后再执行
     * 3. 多轮 ReAct 循环需要异步调度，不能阻塞
     * 4. 最终答案需要实时推送给用户，而工具调用过程中不应输出文本
     *
     * 【解决方案】
     * - 使用 Sinks.Many<String> 作为数据推送通道，将 Flux 暴露给调用者
     * - 使用 scheduleRound() 递归调度每一轮
     * - 使用 RoundState 累积每轮的中间状态
     * - 使用 AtomicBoolean 控制流的生命周期
     *
     * @param conversationId 会话 ID
     * @param question       用户问题
     * @return Flux<String> 流式文本序列
     */
    public Flux<String> streamInternal(String conversationId, String question) {
        // 创建线程安全的消息列表（与非流式相同）
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        boolean useMemory = conversationId != null && chatMemory != null;

        // ===== 以下消息构建逻辑与非流式完全相同 =====
        messages.add(new SystemMessage(REACT_AGENT_SYSTEM_PROMPT));  // ReAct 规则
        messages.add(new SystemMessage(systemPrompt));                // 业务提示

        // 加载历史记忆
        if (useMemory) {
            List<Message> history = chatMemory.get(conversationId);
            if (history != null && !history.isEmpty()) {
                messages.addAll(history);
            }
        }

        // 添加当前用户问题
        messages.add(new UserMessage("<question>" + question + "</question>"));

        // 保存用户问题到记忆
        if (useMemory) {
            chatMemory.add(conversationId, new UserMessage(question));
        }

        // ===== 流式特有的组件 =====

        // Sinks.Many<String> — 数据推送通道
        // 【原理】Sinks 是 Reactor 提供的手动数据推送机制，可以看作一个"水管"：
        // - sink.tryEmitNext(text)：往水管里放一段文本
        // - sink.tryEmitComplete()：关掉水管（正常结束）
        // - sink.tryEmitError(err)：水管破裂（异常结束）
        // - sink.asFlux()：将水管转为 Flux，调用者可以订阅
        //
        // unicast()：单播模式，只允许一个订阅者
        // onBackpressureBuffer()：背压策略，当订阅者处理不过来时，缓冲数据
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 轮次计数器（原子操作，线程安全）
        AtomicLong roundCounter = new AtomicLong(0);

        // 是否已发送最终结果的标记位
        // 【原理】用于控制流的生命周期：一旦设为 true，就不再推送数据、不再调度新轮次
        // 这是防止重复输出和资源泄漏的关键
        AtomicBoolean hasSentFinalResult = new AtomicBoolean(false);

        // 显式初始化（其实 AtomicBoolean 默认就是 false，这里为了代码可读性）
        hasSentFinalResult.set(false);
        roundCounter.set(0);

        // 最终答案缓冲区，用于在流结束时将完整答案保存到记忆
        StringBuilder finalAnswerBuffer = new StringBuilder();

        // 启动第一轮 ReAct 循环
        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId);

        // 返回 Flux，调用者可以订阅获取实时文本
        return sink.asFlux()
                // doOnNext：每推送一个文本片段，就追加到缓冲区
                .doOnNext(finalAnswerBuffer::append)
                // doOnCancel：如果调用者取消了订阅，标记为已完成
                .doOnCancel(() -> hasSentFinalResult.set(true))
                // doFinally：无论流如何结束（完成/取消/错误），都打印最终答案
                .doFinally(signalType -> {
                    log.info("最终答案: {}", finalAnswerBuffer);
                });
    }

    /**
     * 调度一轮 ReAct 循环（流式版本）
     *
     * 【原理】这是流式 ReAct 的核心调度方法，采用递归调度模式：
     * 1. 向大模型发起流式请求
     * 2. 逐 chunk 处理响应（processChunk）
     * 3. 流结束时判断本轮结果（finishRound）
     * 4. 如果有工具调用且未达到上限，递归调用自身开始下一轮
     *
     * 为什么用 subscribe() 而不是 block()？
     * 因为 scheduleRound 本身在 Reactor 的调度线程中执行，
     * block() 会导致线程阻塞，而 subscribe() 是非阻塞的异步订阅。
     *
     * @param messages           消息列表（会随着轮次推进不断追加）
     * @param sink               数据推送通道
     * @param roundCounter       轮次计数器
     * @param hasSentFinalResult 是否已发送最终结果
     * @param finalAnswerBuffer  最终答案缓冲区
     * @param useMemory          是否使用记忆
     * @param conversationId     会话 ID
     */
    private void scheduleRound(List<Message> messages, Sinks.Many<String> sink, AtomicLong roundCounter, AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer, boolean useMemory, String conversationId) {
        // 轮次计数器 +1
        roundCounter.incrementAndGet();

        // 创建本轮的状态容器
        RoundState state = new RoundState();

        // 发起流式请求并订阅
        chatClient.prompt()
                .messages(messages)           // 传入完整消息历史
                .stream()                     // 流式调用（而非 .call()）
                .chatResponse()               // 获取 ChatResponse 类型的流
                .publishOn(Schedulers.boundedElastic())  // 切换到弹性线程池执行后续操作
                // 【原理】publishOn 控制后续操作在哪个调度器上执行：
                // - boundedElastic：适合 I/O 密集型操作（如工具调用、网络请求）
                // - 它会根据需要创建线程，空闲时回收，避免资源浪费
                .doOnNext(chunk -> processChunk(chunk, sink, state))  // 逐 chunk 处理
                .doOnComplete(() -> finishRound(messages, sink, state, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId))  // 流结束处理
                .doOnError(err -> {
                    // 错误处理：只在未发送最终结果时才发送错误
                    // 避免重复关闭流（tryEmitError 在流已关闭时会抛异常）
                    if (!hasSentFinalResult.get()) {
                        hasSentFinalResult.set(true);     // 标记为已结束
                        sink.tryEmitError(err);           // 推送错误给调用者
                    }
                })
                .subscribe();  // 非阻塞订阅，启动流式处理
    }

    /**
     * 处理流式响应中的单个 chunk
     *
     * 【原理】流式响应中，模型输出被拆分为多个 chunk 逐步到达。
     * 每个 chunk 可能包含：
     * - 文本内容（getText()）：模型正在生成的文字
     * - 工具调用（getToolCalls()）：模型决定调用的工具（参数可能不完整）
     * - 两者都有（罕见，但需要处理）
     *
     * 关键逻辑：
     * 1. 如果检测到 ToolCall，立即切换到 TOOL_CALL 模式，停止向用户推送文本
     *    因为工具调用过程中的文本通常是推理过程，不应展示给用户
     * 2. 如果没有 ToolCall，将文本实时推送给用户（流式输出的核心）
     *
     * @param chunk 流式响应的一个片段
     * @param sink  数据推送通道
     * @param state 本轮的状态容器
     */
    private void processChunk(ChatResponse chunk, Sinks.Many<String> sink, RoundState state) {

        // 空值防护：某些 chunk 可能为空或缺少关键字段
        if (chunk == null || chunk.getResult() == null ||
                chunk.getResult().getOutput() == null) return;

        Generation gen = chunk.getResult();                        // 获取生成结果
        String text = gen.getOutput().getText();                   // 获取文本内容
        List<AssistantMessage.ToolCall> tc = gen.getOutput().getToolCalls();  // 获取工具调用列表

        // ===== 检测到工具调用 =====
        // 【关键】一旦发现 tool_call，立即进入 TOOL_CALL 模式
        // 这意味着本轮不会向用户输出最终答案，而是执行工具后继续循环
        if (tc != null && !tc.isEmpty()) {
            state.mode = RoundMode.TOOL_CALL;  // 切换模式

            // 将新的 ToolCall 合并到状态中
            // 【原理】流式响应中，同一个 ToolCall 的参数可能分多个 chunk 到达：
            // chunk1: {"name": "weather", "arguments": "{\"city\": ""}
            // chunk2: {"arguments": "Beiji"}
            // chunk3: {"arguments": "ng\"}"}
            // mergeToolCall 负责将同一 id 的参数拼接起来
            for (AssistantMessage.ToolCall incoming : tc) {
                mergeToolCall(state, incoming);
            }
            return;  // 不输出文本，直接返回
        }

        // ===== 未检测到工具调用，输出文本 =====
        // 【原理】在最终答案模式下，将文本实时推送给用户，实现"打字机效果"
        if (text != null) {
            sink.tryEmitNext(text);       // 推送文本片段给调用者
            state.textBuffer.append(text); // 同时缓存到缓冲区，用于后续保存记忆
        }
    }

    /**
     * 合并流式响应中的工具调用
     *
     * 【原理】在流式响应中，一个完整的工具调用会被拆分为多个 chunk：
     *
     * chunk1: ToolCall(id="call_123", name="weather", arguments="{")
     * chunk2: ToolCall(id="call_123", name=null, arguments="\"city\"")
     * chunk3: ToolCall(id="call_123", name=null, arguments=": \"Beijing\"}")
     *
     * 合并逻辑：
     * - 如果 incoming 的 id 与已有 ToolCall 相同 → 拼接 arguments
     * - 如果是新 id → 添加到列表
     *
     * 最终合并结果：ToolCall(id="call_123", name="weather", arguments="{\"city\": \"Beijing\"}")
     *
     * @param state    本轮状态容器
     * @param incoming 新到达的 ToolCall chunk
     */
    private void mergeToolCall(RoundState state, AssistantMessage.ToolCall incoming) {

        // 遍历已收集的 ToolCall，查找相同 id 的
        for (int i = 0; i < state.toolCalls.size(); i++) {
            AssistantMessage.ToolCall existing = state.toolCalls.get(i);

            // id 相同，说明是同一个工具调用的后续 chunk，需要合并
            if (existing.id().equals(incoming.id())) {

                // 拼接参数：existing.arguments + incoming.arguments
                // Objects.toString 用于安全处理 null 值
                String mergedArgs = Objects.toString(existing.arguments(), "") + Objects.toString(incoming.arguments(), "");

                // 用合并后的参数创建新的 ToolCall，替换原来的
                // 【注意】ToolCall 是不可变对象（record），不能修改，只能创建新的替换
                state.toolCalls.set(i,
                        new AssistantMessage.ToolCall(existing.id(), "function", existing.name(), mergedArgs)
                );
                return;  // 合并完成，返回
            }
        }

        // 没有找到相同 id，说明是新的工具调用，直接添加
        state.toolCalls.add(incoming);
    }


    /**
     * 流式响应一轮结束后的处理
     *
     * 【原理】当一个完整的流式响应结束后（所有 chunk 都已到达），
     * 需要根据本轮的模式决定下一步：
     *
     * 1. 如果是最终答案模式（没有工具调用）
     *    → 保存记忆，关闭流，结束
     *
     * 2. 如果是工具调用模式
     *    → 检查轮次上限 → 执行工具 → 递归调度下一轮
     *
     * @param messages           消息列表
     * @param sink               数据推送通道
     * @param state              本轮状态
     * @param roundCounter       轮次计数器
     * @param hasSentFinalResult 是否已发送最终结果
     * @param finalAnswerBuffer  最终答案缓冲区
     * @param useMemory          是否使用记忆
     * @param conversationId     会话 ID
     */
    private void finishRound(List<Message> messages, Sinks.Many<String> sink, RoundState state, AtomicLong roundCounter,
                             AtomicBoolean hasSentFinalResult, StringBuilder finalAnswerBuffer, boolean useMemory, String conversationId) {

        // ===== 情况1：本轮没有工具调用 → 最终答案 =====
        // 【原理】如果整轮都没有出现 ToolCall，说明模型认为信息足够，
        // 输出的文本就是最终答案
        if (state.mode != RoundMode.TOOL_CALL) {
            String finalText = state.textBuffer.toString();  // 获取完整的最终答案
            sink.tryEmitComplete();                          // 关闭数据通道，通知调用者流结束
            hasSentFinalResult.set(true);                    // 标记为已发送最终结果

            // 保存最终答案到记忆
            if (useMemory) {
                chatMemory.add(conversationId, new AssistantMessage(finalText));
            }
            return;  // 结束，不再调度新轮次
        }

        // ===== 情况2：有工具调用，但已达到轮次上限 =====
        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            // 强制生成最终答案（流式版本）
            forceFinalStream(conversationId, useMemory, messages, sink, hasSentFinalResult);
            return;
        }

        // ===== 情况3：有工具调用，继续 ReAct 循环 =====

        // 将模型的工具调用作为 AssistantMessage 添加到消息列表
        // 【原理】与非流式版本相同，模型返回的 ToolCall 必须以 AssistantMessage 的形式
        // 出现在消息历史中
        AssistantMessage assistantMsg = AssistantMessage.builder().toolCalls(state.toolCalls).build();
        messages.add(assistantMsg);

        // 执行工具调用
        // 【原理】executeToolCalls 是异步执行的，所有工具执行完成后回调 onComplete
        // 回调中递归调度下一轮 scheduleRound
        executeToolCalls(state.toolCalls, messages, hasSentFinalResult, () -> {
            // 所有工具执行完成的回调
            if (!hasSentFinalResult.get()) {
                // 递归调度下一轮
                scheduleRound(messages, sink, roundCounter,
                        hasSentFinalResult, finalAnswerBuffer,
                        useMemory, conversationId);
            }
        });
    }


    /**
     * 强制生成最终答案（流式版本）
     *
     * 【原理】当达到最大推理轮次时，强制模型输出最终答案。
     * 与非流式版本的 forceFinal 逻辑相同，但采用流式输出。
     *
     * 这是一种"安全网"机制，确保 Agent 不会因为模型反复调用工具而无限运行。
     *
     * @param conversationId     会话 ID
     * @param useMemory          是否使用记忆
     * @param messages           消息列表
     * @param sink               数据推送通道
     * @param hasSentFinalResult 是否已发送最终结果
     */
    private void forceFinalStream(String conversationId, boolean useMemory, List<Message> messages, Sinks.Many<String> sink, AtomicBoolean hasSentFinalResult) {
        // 注入强制终止提示
        messages.add(new UserMessage("""
                你已达到最大推理轮次限制。
                请基于当前已有的上下文信息，
                直接给出最终答案。
                禁止再调用任何工具。
                如果信息不完整，请合理总结和说明。
                """));

        // 缓冲区，用于累积最终答案
        StringBuilder stringBuilder = new StringBuilder();

        // 流式调用模型，获取强制最终答案
        chatClient.prompt()
                .messages(messages)
                .stream()                     // 流式调用
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())  // 切换到弹性线程池
                .doOnNext(chunk -> {
                    // 逐 chunk 处理：空值防护
                    if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                        return;
                    }

                    // 提取文本
                    String text = chunk.getResult()
                            .getOutput()
                            .getText();

                    // 如果文本非空且尚未发送最终结果，推送给调用者
                    if (text != null && !hasSentFinalResult.get()) {
                        sink.tryEmitNext(text);         // 推送文本片段
                        stringBuilder.append(text);      // 累积到缓冲区
                    }
                })
                .doOnComplete(() -> {
                    // 流结束：标记为已完成，关闭通道，保存记忆
                    hasSentFinalResult.set(true);         // 标记为已完成
                    sink.tryEmitComplete();               // 关闭数据通道
                    if (useMemory) {
                        chatMemory.add(conversationId, new AssistantMessage(stringBuilder.toString()));  // 保存记忆
                    }
                })
                .doOnError(err -> {
                    // 错误处理
                    hasSentFinalResult.set(true);  // 标记为已完成
                    sink.tryEmitError(err);        // 推送错误
                })
                .subscribe();  // 非阻塞订阅
    }

    /**
     * 异步执行工具调用（流式版本）
     *
     * 【原理】与非流式版本的同步执行不同，这里使用 Schedulers.boundedElastic()
     * 将每个工具调用调度到独立的线程中执行，实现并行工具调用。
     *
     * 并行执行的优势：
     * - 如果模型一次返回了 3 个工具调用（如同时查北京天气、上海天气、搜索预警），
     *   并行执行可以同时发起 3 个请求，总耗时 ≈ 最慢的那个
     *   串行执行则总耗时 = 3 个请求之和
     *
     * 完成回调机制：
     * - 使用 AtomicInteger 计数，每完成一个工具调用 +1
     * - 当所有工具调用都完成时，触发 onComplete 回调
     * - 这是一种 CountDownLatch 的轻量替代方案
     *
     * @param toolCalls          工具调用列表
     * @param messages           消息列表（工具结果会追加到此列表）
     * @param hasSentFinalResult 是否已发送最终结果（用于提前终止）
     * @param onComplete         所有工具执行完成后的回调
     */
    private void executeToolCalls(List<AssistantMessage.ToolCall> toolCalls, List<Message> messages, AtomicBoolean hasSentFinalResult, Runnable onComplete) {
        // 已完成的工具调用计数器
        AtomicInteger completedCount = new AtomicInteger(0);
        // 总工具调用数
        int totalToolCalls = toolCalls.size();

        // 遍历所有工具调用，每个都调度到独立线程执行
        for (AssistantMessage.ToolCall tc : toolCalls) {
            Schedulers.boundedElastic().schedule(() -> {
                // 检查是否已经发送了最终结果（可能被取消或出错）
                // 如果是，直接完成计数，不再执行工具
                if (hasSentFinalResult.get()) {
                    completeToolCall(completedCount, totalToolCalls, onComplete);
                    return;
                }

                String toolName = tc.name();       // 工具名称
                String argsJson = tc.arguments();  // 工具参数（JSON）

                // 查找工具回调
                ToolCallback callback = findTool(toolName);
                if (callback == null) {
                    // 工具未找到，添加错误响应
                    addErrorToolResponse(messages, tc, "工具未找到：" + toolName);
                    completeToolCall(completedCount, totalToolCalls, onComplete);
                    return;
                }

                try {
                    // 执行工具调用
                    Object result = callback.call(argsJson);
                    String resultStr = Objects.toString(result, "");  // 安全转为字符串

                    // 构建工具响应消息
                    ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                            tc.id(),     // 工具调用 ID
                            toolName,     // 工具名称
                            resultStr     // 执行结果
                    );

                    // 添加到消息列表
                    messages.add(ToolResponseMessage.builder()
                            .responses(List.of(tr))
                            .build());
                } catch (Exception ex) {
                    // 工具执行失败，添加错误响应
                    addErrorToolResponse(messages, tc, "工具执行失败：" + ex.getMessage());
                } finally {
                    // 无论成功失败，都要完成计数
                    completeToolCall(completedCount, totalToolCalls, onComplete);
                }
            });
        }
    }

    /**
     * 工具调用完成计数器
     *
     * 【原理】这是一个回调计数模式的实现：
     * - 每完成一个工具调用，计数器 +1
     * - 当计数器达到总数时，触发 onComplete 回调
     *
     * 类似于 CountDownLatch.await()，但是非阻塞的回调方式。
     * 适合在响应式编程中使用，避免线程阻塞。
     *
     * @param completedCount 已完成计数器
     * @param total          总数
     * @param onComplete     全部完成后的回调
     */
    private void completeToolCall(AtomicInteger completedCount, int total, Runnable onComplete) {
        int current = completedCount.incrementAndGet();  // 原子递增并获取当前值
        if (current >= total) {  // 所有工具调用都已完成
            onComplete.run();    // 触发回调
        }
    }

    /**
     * 添加工具执行错误的响应消息
     *
     * 【原理】当工具执行失败或工具不存在时，必须向消息列表中添加一个 ToolResponseMessage，
     * 告知模型工具调用的结果（错误信息）。这是因为：
     *
     * 1. OpenAI API 规范要求每个 ToolCall 都必须有对应的 ToolResponse
     * 2. 如果缺少响应，模型会认为工具还在执行中，无法继续推理
     * 3. 错误信息帮助模型理解失败原因，从而决定下一步（重试/换策略/直接回答）
     *
     * 错误信息格式为 JSON，方便模型解析和理解。
     *
     * @param messages 消息列表
     * @param toolCall 失败的工具调用
     * @param errMsg   错误信息
     */
    private void addErrorToolResponse(List<Message> messages, AssistantMessage.ToolCall toolCall, String errMsg) {
        // 构建错误工具响应，将错误信息包装为 JSON 格式
        ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                toolCall.id(),     // 与原始 ToolCall 的 id 对应
                toolCall.name(),   // 工具名称
                "{ \"error\": \"" + errMsg + "\" }"  // JSON 格式的错误信息
        );

        // 添加到消息列表
        messages.add(ToolResponseMessage.builder()
                .responses(List.of(tr))
                .build());
    }

    /**
     * 根据工具名称查找对应的 ToolCallback
     *
     * 【原理】ToolCallback 包含两部分信息：
     * - ToolDefinition：工具定义（名称、描述、参数 schema），大模型能看到的部分
     * - call() 方法：工具执行逻辑，大模型看不到，由 Agent 代码调用
     *
     * 当模型返回 ToolCall 时，只包含工具名称和参数，
     * 需要通过名称匹配找到对应的 ToolCallback，才能执行工具。
     *
     * @param name 工具名称
     * @return 对应的 ToolCallback，未找到则返回 null
     */
    private ToolCallback findTool(String name) {
        return tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))  // 按工具定义的名称匹配
                .findFirst()       // 找到第一个匹配的
                .orElse(null);     // 未找到返回 null
    }

    /**
     * 创建 Builder 实例
     * 【设计模式】Builder 模式的入口方法，用于构建 SimpleReactAgent 对象。
     * 好处：参数可选、可组合、代码可读性高。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder 模式 — 构建 SimpleReactAgent 的工具类
     *
     * 【设计模式】Builder 模式的优势：
     * 1. 参数可选：不是所有参数都是必需的（如 chatMemory、advisors 可以为 null）
     * 2. 链式调用：builder.name("a").chatModel(model).tools(t).build()，可读性好
     * 3. 参数组合灵活：可以按任意顺序设置参数
     * 4. 延迟构建：在 build() 时才创建对象，可以在构建前进行参数校验
     */
    public static class Builder {
        private String name;                          // Agent 名称
        private ChatModel chatModel;                  // 大模型实例（必须）
        private List<ToolCallback> tools;             // 工具列表（可选）
        private String systemPrompt = "";             // 系统提示词（默认为空字符串）

        private int maxReflectionRounds;              // 最大反思轮次（默认 0，不启用）

        private int maxRounds;                        // 最大推理轮次（默认 0，不限制）

        private List<Advisor> advisors;               // 顾问/拦截器列表（可选）

        private ChatMemory chatMemory;                // 对话记忆（可选）

        /** 设置对话记忆 */
        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;  // 返回 this，支持链式调用
        }

        /** 设置 Agent 名称 */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** 设置大模型实例 */
        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        /** 设置工具列表（可变参数版本） */
        public Builder tools(ToolCallback... tools) {
            this.tools = Arrays.asList(tools);  // 将可变参数转为 List
            return this;
        }

        /** 设置工具列表（List 版本） */
        public Builder tools(List<ToolCallback> tools) {
            this.tools = tools;
            return this;
        }

        /** 设置顾问列表（List 版本） */
        public Builder advisors(List<Advisor> advisors) {
            this.advisors = advisors;
            return this;
        }

        /** 设置顾问列表（可变参数版本） */
        public Builder advisors(Advisor... advisors) {
            this.advisors = Arrays.asList(advisors);
            return this;
        }

        /** 设置系统提示词 */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /** 设置最大反思轮次 */
        public Builder maxReflectionRounds(int maxReflectionRounds) {
            this.maxReflectionRounds = maxReflectionRounds;
            return this;
        }

        /** 设置最大推理轮次 */
        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        /**
         * 构建 SimpleReactAgent 实例
         *
         * 【原理】build() 是 Builder 模式的最终步骤：
         * 1. 校验必需参数（chatModel 不能为空）
         * 2. 调用全参构造函数创建对象
         * 3. 构造函数中会初始化 ChatClient 并进行完整性检查
         *
         * @return SimpleReactAgent 实例
         * @throws IllegalArgumentException 如果 chatModel 为空
         */
        public SimpleReactAgent build() {
            // 必需参数校验：chatModel 是 Agent 的核心，不能为空
            if (chatModel == null) {
                throw new IllegalArgumentException("chatModel 不能为空！");
            }
            // 调用全参构造函数，传入所有参数
            return new SimpleReactAgent(name, chatModel, tools, systemPrompt, maxRounds, chatMemory, advisors, maxReflectionRounds);
        }
    }

    /**
     * 主方法 — 示例用法和快速测试
     *
     * 这个 main 方法展示了如何构建和使用 SimpleReactAgent：
     * 1. 创建 ChatModel（通过配置工厂）
     * 2. 创建工具（天气服务、搜索服务）
     * 3. 创建对话记忆（滑动窗口，最多保留 20 条消息）
     * 4. 构建 Agent（设置名称、模型、工具、记忆、轮次上限、系统提示词）
     * 5. 提问并获取答案
     *
     * 【示例问题的 ReAct 执行流程】
     * 问题需要获取 4 个信息：北京今天天气、未来七天趋势、上海今天天气、北京预警
     * 可能的执行流程：
     * Round 1: 模型推理 → 调用 getWeather(city="北京") + getWeatherTrend(city="北京")
     * Round 2: 模型推理 → 调用 getWeather(city="上海") + search(query="北京天气预警")
     * Round 3: 模型推理 → 信息充足，生成 600 字综合分析报告（最终答案）
     */
    public static void main(String[] args) {
        // 1. 获取 ChatModel 实例（通过配置工厂，内部配置了 API Key、模型名称等）
        ChatModel chatModel = ChatModelConfig.getChatModel();

        // 2. 从服务类创建工具回调
        // 【原理】ToolCallbacks.from() 会扫描类中标注了 @Tool 注解的方法，
        // 自动生成 ToolDefinition（名称、描述、参数 schema）和执行逻辑
        ToolCallback[] toolCallbacks = ToolCallbacks.from(new WeatherService(), new SearchService());

        // 3. 创建对话记忆
        // 【原理】MessageWindowChatMemory 使用滑动窗口策略：
        // - maxMessages(20)：最多保留最近 20 条消息
        // - 超出时自动删除最旧的消息
        // - 这是为了控制 token 消耗，避免消息过长导致 API 费用过高或超出上下文窗口
        ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(20).build();

        // 4. 构建 Agent
        SimpleReactAgent agent = SimpleReactAgent.builder()
                .name("simple-agent")                          // Agent 名称
                .chatModel(chatModel)                          // 大模型实例
                .tools(toolCallbacks)                          // 工具列表
                .chatMemory(chatMemory)                        // 对话记忆
                .maxRounds(5)                                  // 最多 5 轮推理
                .systemPrompt("你是专业的研究分析助手！")       // 业务提示词
                .build();                                      // 构建实例

        // 5. 构建测试问题
        // 这个问题需要多次工具调用才能完成，非常适合测试 ReAct 模式
        String question = """
                请你根据北京今天的天气、未来七天的天气趋势、以及上海今天的天气，并搜索北京天气的预警情况，生成一份不少于 600 字的综合分析报告。
                """;

        // 非流式调用（已注释）
//        System.out.println(agent.call(question));

        // 流式调用：实时输出每个文本片段
        // doOnNext：每收到一个文本片段就打印（不换行，模拟打字机效果）
        // blockLast()：阻塞等待整个流完成
        agent.stream(question).doOnNext(chuck -> {
            System.out.print(chuck);
        }).blockLast();
    }
}
