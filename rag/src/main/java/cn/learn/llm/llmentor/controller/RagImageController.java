package cn.learn.llm.llmentor.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.chat.MessageFormat;
import com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/5/1 16:33
 */
@RestController
@RequestMapping("/rag/image")
public class RagImageController {

    @Autowired
    private ChatModel chatModel;

    @RequestMapping("/callWithOpenAI")
    public String callWithOpenAI() throws URISyntaxException, MalformedURLException {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .temperature(0.2d)
                .model("qwen3-vl-plus")
                .build();

        OpenAiChatModel multimodalChatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/")
                        .apiKey(new SimpleApiKey("sk-7cce5d692dda4b64b4aa1e9d7ff1452b"))
                        .build()
                )
                .defaultOptions(options)
                .build();


        List<Media> mediaList = List.of(new Media(MimeTypeUtils.IMAGE_JPEG,
                new URI("https://cdn.nlark.com/yuque/0/2025/png/5378072/1762350625634-664f1db7-e1c9-4daa-ab8e-81b6b7da5a68.png").toURL().toURI()));

        UserMessage userMessage = UserMessage.builder()
                .text("请非常简要的描述一下你看到的这个图片?")
                .media(mediaList)
                .build();

        var response = multimodalChatModel.call(new Prompt(List.of(userMessage)));

        return response.getResult().getOutput().getText();
    }


    @RequestMapping("/callWithSpringAiAlibaba")
    public String callWithSpringAiAlibaba() throws URISyntaxException, MalformedURLException {
        List<Media> mediaList = List.of(new Media(MimeTypeUtils.IMAGE_JPEG,
                new URI("https://cdn.nlark.com/yuque/0/2025/png/5378072/1762350625634-664f1db7-e1c9-4daa-ab8e-81b6b7da5a68.png")
                        .toURL()
                        .toURI()));
        var userMessage = UserMessage.builder()
                .text("请详细的描述一下你看到的这个图片?字数控制在600字以内")
                .media(mediaList)
                .build();

        return chatModel.call(new Prompt(userMessage,
                DashScopeChatOptions.builder()
                        .withModel("qwen3-vl-plus")
                        .withMultiModel(true)
                        .temperature(0.2d)
                        .build())).getResult().getOutput().getText();
    }


    @RequestMapping("/callWithChatClient")
    public String callWithChatClient() throws URISyntaxException, MalformedURLException {
        List<Media> mediaList = List.of(new Media(MimeTypeUtils.IMAGE_PNG,
                new URI("https://dashscope.oss-cn-beijing.aliyuncs.com/images/dog_and_girl.jpeg")
                        .toURL()
                        .toURI()));
        UserMessage message = UserMessage.builder()
                .text("请非常简要的描述一下你看到的这个图片?")
                .media(mediaList)
                .metadata(new HashMap<>())
                .build();

        message.getMetadata().put(DashScopeApiConstants.MESSAGE_FORMAT, MessageFormat.IMAGE);
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen3-vl-plus")
                        .build()
                )
                .build();
        ChatResponse response = chatClient.prompt(new Prompt(message, DashScopeChatOptions.builder()
                        .withModel("qwen3-vl-plus")
                        .withMultiModel(true).build()))
                .call()
                .chatResponse();
        return response.getResult().getOutput().getText();
    }


}
