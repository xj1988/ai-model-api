package com.github.aimodel.chat.model;

import com.github.aimodel.chat.messages.AssistantMessage;
import com.github.aimodel.chat.messages.MessageType;
import com.github.aimodel.chat.messages.ToolResponseMessage;
import com.github.aimodel.chat.metadata.*;
import com.github.aimodel.chat.prompt.Prompt;
import com.github.aimodel.util.UsageUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MoonshotChatModel is a {@link ChatModel} implementation that uses the Moonshot
 *
 * @author Geng Rong
 * @author Alexandros Pappas
 * @author Ilayaperumal Gopinathan
 */
public class MoonshotChatModel implements ChatModel, StreamingChatModel {

    private MoonshotApi moonshotApi;

    public MoonshotChatModel(MoonshotApi moonshotApi) {
        this.moonshotApi = moonshotApi;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return internalCall(prompt, null);
    }

    public ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {
        MoonshotApi.ChatCompletionRequest request = createRequest(prompt, false);

        ResponseEntity<MoonshotApi.ChatCompletion> completionEntity = this.moonshotApi.chatCompletionEntity(request);
        MoonshotApi.ChatCompletion chatCompletion = completionEntity.getBody();

        if (chatCompletion == null) {
            //logger.warn("No chat completion returned for prompt: {}", prompt);
            return new ChatResponse(new ArrayList<>());
        }

        List<MoonshotApi.Choice> choices = chatCompletion.getChoices();
        if (choices == null) {
            //logger.warn("No choices returned for prompt: {}", prompt);
            return new ChatResponse(new ArrayList<>());
        }

        List<Generation> generations = choices.stream().map(choice -> {
            // @formatter:off
            Map<String, Object> metadata = new HashMap<String, Object>() {{
                put("id", chatCompletion.getId());
                put("role", choice.getMessage().getRole() != null ? choice.getMessage().getRole().name() : "");
                put("finishReason", choice.getFinishReason() != null ? choice.getFinishReason().name() : "");
            }};
            // @formatter:on
            return buildGeneration(choice, metadata);
        }).collect(Collectors.toList());

        MoonshotApi.Usage usage = completionEntity.getBody().getUsage();
        Usage currentUsage = (usage != null) ? getDefaultUsage(usage) : new EmptyUsage();
        Usage cumulativeUsage = UsageUtils.getCumulativeUsage(currentUsage, previousChatResponse);
        return new ChatResponse(generations, from(completionEntity.getBody(), cumulativeUsage));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return internalStream(prompt, null);
    }

    public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
        MoonshotApi.ChatCompletionRequest request = createRequest(prompt, true);

        Flux<MoonshotApi.ChatCompletionChunk> completionChunks = this.moonshotApi.chatCompletionStream(request);

        // For chunked responses, only the first chunk contains the choice role.
        // The rest of the chunks with same ID share the same role.
        ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

        // Convert the ChatCompletionChunk into a ChatCompletion to be able to reuse
        // the function call handling logic.
        return completionChunks.map(this::chunkToChatCompletion)
                .switchMap(chatCompletion -> Mono.just(chatCompletion).map(chatCompletion2 -> {
                    try {
                        String id = chatCompletion2.getId();

                        List<Generation> generations = chatCompletion2.getChoices().stream().map(choice -> {
                            if (choice.getMessage().getRole() != null) {
                                roleMap.putIfAbsent(id, choice.getMessage().getRole().name());
                            }

                            // @formatter:off
                            Map<String, Object> metadata = new HashMap<String, Object>() {{
                                put("id", chatCompletion2.getId());
                                put("role", roleMap.getOrDefault(id, ""));
                                put("finishReason", choice.getFinishReason() != null ? choice.getFinishReason().name() : "");
                            }};
                            // @formatter:on
                            return buildGeneration(choice, metadata);
                        }).collect(Collectors.toList());

                        MoonshotApi.Usage usage = chatCompletion2.getUsage();
                        Usage currentUsage = (usage != null) ? getDefaultUsage(usage) : new EmptyUsage();
                        Usage cumulativeUsage = UsageUtils.getCumulativeUsage(currentUsage, previousChatResponse);

                        return new ChatResponse(generations, from(chatCompletion2, cumulativeUsage));
                    } catch (Exception e) {
                        //logger.error("Error processing chat completion", e);
                        return new ChatResponse(new ArrayList<>());
                    }
                }));

    }

    private ChatResponseMetadata from(MoonshotApi.ChatCompletion result, Usage usage) {
        Assert.notNull(result, "Moonshot ChatCompletionResult must not be null");
        return ChatResponseMetadata.builder()
                .id(result.getId() != null ? result.getId() : "")
                .usage(usage)
                .model(result.getModel() != null ? result.getModel() : "")
                .keyValue("created", result.getCreated() != null ? result.getCreated() : 0L)
                .build();
    }

    private DefaultUsage getDefaultUsage(MoonshotApi.Usage usage) {
        return new DefaultUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens(), usage);
    }

    private static Generation buildGeneration(MoonshotApi.Choice choice, Map<String, Object> metadata) {
        List<AssistantMessage.ToolCall> toolCalls = choice.getMessage().getToolCalls() == null ? new ArrayList<>()
                : choice.getMessage()
                .getToolCalls()
                .stream()
                .map(toolCall -> new AssistantMessage.ToolCall(toolCall.getId(), "function",
                        toolCall.getFunction().getName(), toolCall.getFunction().getArguments()))
                .collect(Collectors.toList());

        AssistantMessage assistantMessage = new AssistantMessage(choice.getMessage().content(), metadata, toolCalls);
        String finishReason = (choice.getFinishReason() != null ? choice.getFinishReason().name() : "");
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();
        return new Generation(assistantMessage, generationMetadata);
    }

    /**
     * Accessible for testing.
     */
    public MoonshotApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
        List<MoonshotApi.ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions().stream().map(message -> {
            if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
                Object content = message.getText();
                return Collections.singletonList(new MoonshotApi.ChatCompletionMessage(content,
                        MoonshotApi.Role.valueOf(message.getMessageType().name())));
            } else if (message.getMessageType() == MessageType.ASSISTANT) {
                AssistantMessage assistantMessage = (AssistantMessage) message;
                List<MoonshotApi.ToolCall> toolCalls = null;
                if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                    toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
                        MoonshotApi.ChatCompletionFunction function = new MoonshotApi.ChatCompletionFunction(toolCall.getName(), toolCall.getArguments());
                        return new MoonshotApi.ToolCall(toolCall.getId(), toolCall.getType(), function);
                    }).collect(Collectors.toList());
                }
                return Collections.singletonList(new MoonshotApi.ChatCompletionMessage(assistantMessage.getText(),
                        MoonshotApi.Role.ASSISTANT, null, null, toolCalls));
            } else if (message.getMessageType() == MessageType.TOOL) {
                ToolResponseMessage toolMessage = (ToolResponseMessage) message;

                toolMessage.getResponses()
                        .forEach(response -> Assert.isTrue(response.getId() != null, "ToolResponseMessage must have an id"));

                return toolMessage.getResponses()
                        .stream()
                        .map(tr -> new MoonshotApi.ChatCompletionMessage(tr.getResponseData(), MoonshotApi.Role.TOOL, tr.getName(),
                                tr.getId(), null))
                        .collect(Collectors.toList());
            } else {
                throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
            }
        }).flatMap(List::stream).collect(Collectors.toList());

        return new MoonshotApi.ChatCompletionRequest(chatCompletionMessages, stream);
    }

    /**
     * Convert the ChatCompletionChunk into a ChatCompletion. The Usage is set to null.
     *
     * @param chunk the ChatCompletionChunk to convert
     * @return the ChatCompletion
     */
    private MoonshotApi.ChatCompletion chunkToChatCompletion(MoonshotApi.ChatCompletionChunk chunk) {
        List<MoonshotApi.Choice> choices = chunk.getChoices().stream().map(cc -> {
            MoonshotApi.ChatCompletionMessage delta = cc.getDelta();
            if (delta == null) {
                delta = new MoonshotApi.ChatCompletionMessage("", MoonshotApi.Role.ASSISTANT);
            }
            return new MoonshotApi.Choice(cc.getIndex(), delta, cc.getFinishReason(), cc.getUsage());
        }).collect(Collectors.toList());
        // Get the usage from the latest choice
        MoonshotApi.Usage usage = choices.get(choices.size() - 1).getUsage();
        return new MoonshotApi.ChatCompletion(chunk.getId(), "chat.completion", chunk.getCreated(), chunk.getModel(), choices, usage);
    }

}
