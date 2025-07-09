/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.aimodel.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.aimodel.chat.metadata.ChatResponseMetadata;
import com.github.aimodel.chat.metadata.DefaultUsage;
import com.github.aimodel.chat.metadata.Usage;
import com.github.aimodel.chat.model.*;
import com.github.aimodel.chat.messages.AssistantMessage;
import com.github.aimodel.chat.messages.Message;
import com.github.aimodel.chat.messages.SystemMessage;
import com.github.aimodel.chat.messages.UserMessage;
import com.github.aimodel.chat.prompt.DefaultChatOptionsBuilder;
import com.github.aimodel.chat.prompt.Prompt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit Tests for {@link ChatModel}.
 *
 * @author John Blum
 * @since 0.2.0
 */
class ChatModelTests {

    @Test
    void generateWithStringCallsGenerateWithPromptAndReturnsResponseCorrectly() {

        String userMessage = "Zero Wing";
        String responseMessage = "All your bases are belong to us";

        ChatModel mockClient = Mockito.mock(ChatModel.class);

        AssistantMessage mockAssistantMessage = Mockito.mock(AssistantMessage.class);
        given(mockAssistantMessage.getText()).willReturn(responseMessage);

        // Create a mock Generation
        Generation generation = Mockito.mock(Generation.class);
        given(generation.getOutput()).willReturn(mockAssistantMessage);

        // Create a mock ChatResponse with the mock Generation
        ChatResponse response = Mockito.mock(ChatResponse.class);
        given(response.getResult()).willReturn(generation);

        // Generation generation = spy(new Generation(responseMessage));
        // ChatResponse response = spy(new
        // ChatResponse(Collections.singletonList(generation)));

        doCallRealMethod().when(mockClient).call(anyString());

        given(mockClient.call(any(Prompt.class))).willAnswer(invocationOnMock -> {
            Prompt prompt = invocationOnMock.getArgument(0);

            assertThat(prompt).isNotNull();
            assertThat(prompt.getContents()).isEqualTo(userMessage);

            return response;
        });

        assertThat(mockClient.call(userMessage)).isEqualTo(responseMessage);

        verify(mockClient, times(1)).call(eq(userMessage));
        verify(mockClient, times(1)).call(isA(Prompt.class));
        verify(response, times(1)).getResult();
        verify(generation, times(1)).getOutput();
        verify(mockAssistantMessage, times(1)).getText();
        verifyNoMoreInteractions(mockClient, generation, response);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "YOUR_MODEL_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "YOUR_API_KEY", matches = ".+")
    void testVolcanoEngineChatModelIntegration() {
        // 从环境变量获取配置
        String modelId = System.getenv("YOUR_MODEL_ID");
        String apiKey = System.getenv("YOUR_API_KEY");

        // 实现火山引擎ChatModel的匿名内部类
        ChatModel volcanoChatModel = prompt -> {
            // Prompt转换为火山引擎API请求格式
            List<Map<String, String>> apiMessages = new ArrayList<>();
            for (Message message : prompt.getInstructions()) {
                Map<String, String> apiMessage = new HashMap<>();
                if (message instanceof SystemMessage) {
                    apiMessage.put("role", "system");
                } else if (message instanceof UserMessage) {
                    apiMessage.put("role", "user");
                } else if (message instanceof AssistantMessage) {
                    apiMessage.put("role", "assistant");
                }
                apiMessage.put("content", message.getText());
                apiMessages.add(apiMessage);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelId);
            body.put("messages", apiMessages);

            // 发起请求
            try {
                URL url = new URL("https://ark.cn-beijing.volces.com/api/v3/chat/completions");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setDoOutput(true);

                // 发送请求体
                ObjectMapper objectMapper = new ObjectMapper();
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to convert body to JSON", e);
                }

                // 检查响应状态
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new RuntimeException("HTTP request failed with response code: " + responseCode);
                }

                // 读取响应内容
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(),
                        StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                } finally {
                    connection.disconnect();
                }
                String apiResponse = response.toString();

                // 解析响应
                JsonNode rootNode = objectMapper.readTree(apiResponse);

                // 解析usage信息
                JsonNode usageNode = rootNode.get("usage");
                Integer promptTokens = usageNode != null ? usageNode.get("prompt_tokens").asInt() : 0;
                Integer completionTokens = usageNode != null ? usageNode.get("completion_tokens").asInt() : 0;
                Integer totalTokens = usageNode != null ? usageNode.get("total_tokens").asInt() : 0;
                Usage usage = new DefaultUsage(promptTokens, completionTokens, totalTokens);

                // 解析choices信息
                JsonNode choicesNode = rootNode.get("choices");
                if (choicesNode != null && choicesNode.isArray() && choicesNode.size() > 0) {
                    JsonNode messageNode = choicesNode.get(0).get("message");
                    String content = messageNode.get("content").asText();

                    String id = rootNode.get("id").asText();
                    String model = rootNode.get("model").asText();
                    String created = rootNode.get("created").asText();
                    ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder().id(id).usage(usage).model(model)
                            .keyValue("created", created);

                    return new ChatResponse(Collections.singletonList(new Generation(new AssistantMessage(content))), builder.build());
                }
                throw new RuntimeException("No valid response from API");
            } catch (MalformedURLException e) {
                throw new RuntimeException("Invalid API URL", e);
            } catch (IOException e) {
                throw new RuntimeException("HTTP request failed or response parsing error", e);
            }
        };

        // 创建测试用Prompt
        Prompt testPrompt = new Prompt(Arrays.asList(
                new SystemMessage("You are a helpful assistant."),
                new UserMessage("Hello!")
        ));

        // 执行测试并验证结果
        ChatResponse response = volcanoChatModel.call(testPrompt);
        assertThat(response).isNotNull();
        assertThat(response.getResult().getOutput().getText()).isNotNull();
        assertThat(response.getMetadata().getId()).isNotNull();
        assertThat(response.getMetadata().getModel()).isNotNull();
        assertThat(response.getMetadata().getUsage()).isNotNull();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "YOUR_MODEL_ID", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "YOUR_API_KEY", matches = ".+")
    void chatCompletionStream() {
        // 从环境变量获取配置
        String modelId = System.getenv("YOUR_MODEL_ID");
        String apiKey = System.getenv("YOUR_API_KEY");

        ChatModel volcanoChatModel = new MoonshotChatModel(new MoonshotApi("https://ark.cn-beijing.volces.com/", apiKey));
        Flux<ChatResponse> response = volcanoChatModel.stream(new Prompt(new UserMessage("Hello world"),
                new DefaultChatOptionsBuilder().model(modelId).temperature(0.8).build()));

        assertThat(response).isNotNull();
        assertThat(response.collectList().block()).isNotNull();
    }

}
