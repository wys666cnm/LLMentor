package cn.learn.llm.llmentor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/10 10:27
 */
public class HttpClientCaller {

    private static final String API_KEY = "sk-1e03ea92532b4ca4a8a74d91858d35fa";

    private static final String API_URL = "https://api.deepseek.com/chat/completions";


    public static void main(String[] args) throws IOException, InterruptedException {

        String requestBody = """
                  {
                    "model": "deepseek-chat",
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are a helpful assistant."
                        },
                        {
                            "role": "user",
                            "content": "你好，介绍下当前AI大模型开发的行业趋势，以及现在是否值得入坑？"
                        }
                    ],
                    "stream": false
                }
                """;

        HttpClient client = HttpClient.newHttpClient();

         HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .header("X-DashScope-SSE", "enable")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();

         HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println(response.body());
    }
}
