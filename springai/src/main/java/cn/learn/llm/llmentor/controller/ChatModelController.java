package cn.learn.llm.llmentor.controller;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;


@RestController
@RequestMapping("/model")
public class ChatModelController {

    @Autowired
    private ChatModel chatModel;

    @RequestMapping("/call/string")
    public String callString(String message) {
        return chatModel.call(message);
    }

    @RequestMapping("/call/messages")
    public String callMessages(String message) {
        SystemMessage systemMessage = new SystemMessage("你是一个翻译工具，请把用户的消息翻译成英文");
        Message userMsg = new UserMessage(message);
        Prompt prompt = new Prompt(systemMessage, userMsg);
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    @RequestMapping("/call/prompt")
    public String callPrompt(String message) {
        SystemMessage systemMessage = new SystemMessage("请如实回答我的问题");
        Message userMsg = new UserMessage(message);

        Prompt prompt = new Prompt.Builder()
                .messages(systemMessage, userMsg)
                .chatOptions(OpenAiChatOptions.builder().model("deepseek-chat").build())
                .build();
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    /**
     * 流式输出
     */
    @RequestMapping("/stream/string")
    public Flux<String> callStreamString(String message) {
        return chatModel.stream(new Prompt(message))
                .map(response -> response.getResult().getOutput().getText());
    }
}
