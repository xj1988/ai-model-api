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

package com.github.aimodel.chat.model;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.aimodel.chat.prompt.ChatOptions;
import com.github.aimodel.util.ModelOptionsUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Single-class, Java Client library for Moonshot platform. Provides implementation for
 * the <a href="https://platform.moonshot.cn/docs/api-reference">Chat Completion</a> APIs.
 * <p>
 * Implements <b>Synchronous</b> and <b>Streaming</b> chat completion.
 * </p>
 *
 * @author Geng Rong
 * @author Thomas Vitale
 */
public class MoonshotApi {

    private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

    private final WebClient webClient;

    private final MoonshotStreamFunctionCallingHelper chunkMerger = new MoonshotStreamFunctionCallingHelper();

    /**
     * Create a new client api.
     *
     * @param baseUrl        api base URL.
     * @param moonshotApiKey Moonshot api Key.
     */
    public MoonshotApi(String baseUrl, String moonshotApiKey) {
        Consumer<HttpHeaders> jsonContentHeaders = headers -> {
            headers.setBearerAuth(moonshotApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
        };

        this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
    }

    /**
     * Creates a model response for the given chat conversation.
     *
     * @param chatRequest The chat completion request.
     * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
     * and headers.
     */
    public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {
        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(!chatRequest.getStream(), "Request must set the stream property to false.");

        Mono<ResponseEntity<ChatCompletion>> responseEntityMono = webClient.post()
                .bodyValue(chatRequest)
                .retrieve()
                .toEntity(ChatCompletion.class);

        // Blocks the current thread until the Mono emits an item or an error
        return responseEntityMono.block();
    }

    /**
     * Creates a streaming chat response for the given chat conversation.
     *
     * @param chatRequest The chat completion request. Must have the stream property set
     *                    to true.
     * @return Returns a {@link Flux} stream from chat completion chunks.
     */
    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {
        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(chatRequest.getStream(), "Request must set the steam property to true.");
        AtomicBoolean isInsideTool = new AtomicBoolean(false);

        return this.webClient.post()
                .uri("/api/v3/chat/completions")
                .body(Mono.just(chatRequest), ChatCompletionRequest.class)
                .retrieve()
                .bodyToFlux(String.class)
                // cancels the flux stream after the "[DONE]" is received.
                .takeUntil(SSE_DONE_PREDICATE)
                // filters out the "[DONE]" message.
                .filter(SSE_DONE_PREDICATE.negate())
                .map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class))
                // Detect is the chunk is part of a streaming function call.
                .map(chunk -> {
                    if (this.chunkMerger.isStreamingToolFunctionCall(chunk)) {
                        isInsideTool.set(true);
                    }
                    return chunk;
                })
                // Group all chunks belonging to the same function call.
                // Flux<ChatCompletionChunk> -> Flux<Flux<ChatCompletionChunk>>
                .windowUntil(chunk -> {
                    if (isInsideTool.get() && this.chunkMerger.isStreamingToolFunctionCallFinish(chunk)) {
                        isInsideTool.set(false);
                        return true;
                    }
                    return !isInsideTool.get();
                })
                // Merging the window chunks into a single chunk.
                // Reduce the inner Flux<ChatCompletionChunk> window into a single
                // Mono<ChatCompletionChunk>,
                // Flux<Flux<ChatCompletionChunk>> -> Flux<Mono<ChatCompletionChunk>>
                .concatMapIterable(window -> {
                    Mono<ChatCompletionChunk> monoChunk = window.reduce(
                            new ChatCompletionChunk(null, null, null, null, null),
                            this.chunkMerger::merge);
                    return Collections.singletonList(monoChunk);
                })
                // Flux<Mono<ChatCompletionChunk>> -> Flux<ChatCompletionChunk>
                .flatMap(mono -> mono);
    }

    public static class MoonshotStreamFunctionCallingHelper {

        public ChatCompletionChunk merge(ChatCompletionChunk previous, ChatCompletionChunk current) {
            if (previous == null) {
                return current;
            } else {
                String id = current.getId() != null ? current.getId() : previous.getId();
                Long created = current.getCreated() != null ? current.getCreated() : previous.getCreated();
                String model = current.getModel() != null ? current.getModel() : previous.getModel();
                String object = current.getObject() != null ? current.getObject() : previous.getObject();
                ChatCompletionChunk.ChunkChoice previousChoice0 = CollectionUtils.isEmpty(previous.getChoices()) ? null : previous.getChoices().get(0);
                ChatCompletionChunk.ChunkChoice currentChoice0 = CollectionUtils.isEmpty(current.getChoices()) ? null : current.getChoices().get(0);
                ChatCompletionChunk.ChunkChoice choice = this.merge(previousChoice0, currentChoice0);
                List<ChatCompletionChunk.ChunkChoice> chunkChoices = choice == null ? new ArrayList<>() : Collections.singletonList(choice);
                return new ChatCompletionChunk(id, object, created, model, chunkChoices);
            }
        }

        private ChatCompletionChunk.ChunkChoice merge(ChatCompletionChunk.ChunkChoice previous, ChatCompletionChunk.ChunkChoice current) {
            if (previous == null) {
                return current;
            } else {
                ChatCompletionFinishReason finishReason = current.getFinishReason() != null ? current.getFinishReason() : previous.getFinishReason();
                Integer index = current.getIndex() != null ? current.getIndex() : previous.getIndex();
                Usage usage = current.getUsage() != null ? current.getUsage() : previous.getUsage();
                ChatCompletionMessage message = this.merge(previous.getDelta(), current.getDelta());
                return new ChatCompletionChunk.ChunkChoice(index, message, finishReason, usage);
            }
        }

        private ChatCompletionMessage merge(ChatCompletionMessage previous, ChatCompletionMessage current) {
            String content = current.content() != null ? current.content() :
                    (previous.content() != null ? previous.content() : "");

            Role role = current.getRole() != null ? current.getRole() : previous.getRole();
            role = role != null ? role : Role.ASSISTANT;
            String name = current.getName() != null ? current.getName() : previous.getName();
            String toolCallId = current.getToolCallId() != null ? current.getToolCallId() : previous.getToolCallId();
            List<ToolCall> toolCalls = new ArrayList<>();
            ToolCall lastPreviousTooCall = null;
            if (previous.getToolCalls() != null) {
                lastPreviousTooCall = previous.getToolCalls().get(previous.getToolCalls().size() - 1);
                if (previous.getToolCalls().size() > 1) {
                    toolCalls.addAll(previous.getToolCalls().subList(0, previous.getToolCalls().size() - 1));
                }
            }

            if (current.getToolCalls() != null) {
                if (current.getToolCalls().size() > 1) {
                    throw new IllegalStateException("Currently only one tool call is supported per message!");
                }

                ToolCall currentToolCall = current.getToolCalls().iterator().next();
                if (currentToolCall.getId() != null) {
                    if (lastPreviousTooCall != null) {
                        toolCalls.add(lastPreviousTooCall);
                    }

                    toolCalls.add(currentToolCall);
                } else {
                    toolCalls.add(this.merge(lastPreviousTooCall, currentToolCall));
                }
            } else if (lastPreviousTooCall != null) {
                toolCalls.add(lastPreviousTooCall);
            }

            return new ChatCompletionMessage(content, role, name, toolCallId, toolCalls);
        }

        private ToolCall merge(ToolCall previous, ToolCall current) {
            if (previous == null) {
                return current;
            } else {
                String id = current.getId() != null ? current.getId() : previous.getId();
                String type = current.getType() != null ? current.getType() : previous.getType();
                ChatCompletionFunction function = this.merge(previous.getFunction(), current.getFunction());
                return new ToolCall(id, type, function);
            }
        }

        private ChatCompletionFunction merge(ChatCompletionFunction previous, ChatCompletionFunction current) {
            if (previous == null) {
                return current;
            } else {
                String name = current.getName() != null ? current.getName() : previous.getName();
                StringBuilder arguments = new StringBuilder();
                if (previous.getArguments() != null) {
                    arguments.append(previous.getArguments());
                }

                if (current.getArguments() != null) {
                    arguments.append(current.getArguments());
                }

                return new ChatCompletionFunction(name, arguments.toString());
            }
        }

        public boolean isStreamingToolFunctionCall(ChatCompletionChunk chatCompletion) {
            if (chatCompletion != null && !CollectionUtils.isEmpty(chatCompletion.getChoices())) {
                ChatCompletionChunk.ChunkChoice choice = chatCompletion.getChoices().get(0);
                if (choice != null && choice.getDelta() != null) {
                    return !CollectionUtils.isEmpty(choice.getDelta().getToolCalls());
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        public boolean isStreamingToolFunctionCallFinish(ChatCompletionChunk chatCompletion) {
            if (chatCompletion != null && !CollectionUtils.isEmpty(chatCompletion.getChoices())) {
                ChatCompletionChunk.ChunkChoice choice = chatCompletion.getChoices().get(0);
                if (choice != null && choice.getDelta() != null) {
                    return choice.getFinishReason() == ChatCompletionFinishReason.TOOL_CALLS;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

    }

    /**
     * Chat completion request.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletionRequest {

        // You may need to adjust this
        private static final String DEFAULT_CHAT_MODEL = "gpt-3.5-turbo";

        @JsonProperty("messages")
        private List<ChatCompletionMessage> messages;

        @JsonProperty("model")
        private String model;

        @JsonProperty("max_tokens")
        private Integer maxTokens;

        @JsonProperty("temperature")
        private Double temperature;

        @JsonProperty("top_p")
        private Double topP;

        @JsonProperty("n")
        private Integer n;

        @JsonProperty("frequency_penalty")
        private Double frequencyPenalty;

        @JsonProperty("presence_penalty")
        private Double presencePenalty;

        @JsonProperty("stop")
        private List<String> stop;

        @JsonProperty("stream")
        private Boolean stream;

        @JsonProperty("tools")
        private List<FunctionTool> tools;

        @JsonProperty("tool_choice")
        private Object toolChoice;

        public ChatCompletionRequest() {

        }

        // Full constructor
        public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Integer maxTokens,
                                     Double temperature, Double topP, Integer n, Double frequencyPenalty,
                                     Double presencePenalty, List<String> stop, Boolean stream,
                                     List<FunctionTool> tools, Object toolChoice) {
            this.messages = messages;
            this.model = model;
            this.maxTokens = maxTokens;
            this.temperature = temperature;
            this.topP = topP;
            this.n = n;
            this.frequencyPenalty = frequencyPenalty;
            this.presencePenalty = presencePenalty;
            this.stop = stop;
            this.stream = stream;
            this.tools = tools;
            this.toolChoice = toolChoice;
        }

        /**
         * Shortcut constructor for a chat completion request with the given messages and
         * model.
         *
         * @param messages The prompt(s) to generate completions for, encoded as a list of
         *                 dict with role and content. The first prompt role should be user or system.
         * @param model    ID of the model to use.
         */
        public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model) {
            this(messages, model, null, 0.3, 1.0, null, null, null, null, false, null, null);
        }

        /**
         * Shortcut constructor for a chat completion request with the given messages,
         * model and temperature.
         *
         * @param messages    The prompt(s) to generate completions for, encoded as a list of
         *                    dict with role and content. The first prompt role should be user or system.
         * @param model       ID of the model to use.
         * @param temperature What sampling temperature to use, between 0.0 and 1.0.
         * @param stream      Whether to stream back partial progress. If set, tokens will be
         *                    sent
         */
        public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature,
                                     boolean stream) {
            this(messages, model, null, temperature, 1.0, null, null, null, null, stream, null, null);
        }

        /**
         * Shortcut constructor for a chat completion request with the given messages,
         * model and temperature.
         *
         * @param messages    The prompt(s) to generate completions for, encoded as a list of
         *                    dict with role and content. The first prompt role should be user or system.
         * @param model       ID of the model to use.
         * @param temperature What sampling temperature to use, between 0.0 and 1.0.
         */
        public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature) {
            this(messages, model, null, temperature, 1.0, null, null, null, null, false, null, null);
        }

        /**
         * Shortcut constructor for a chat completion request with the given messages,
         * model, tools and tool choice. Streaming is set to false, temperature to 0.8 and
         * all other parameters are null.
         *
         * @param messages   A list of messages comprising the conversation so far.
         * @param model      ID of the model to use.
         * @param tools      A list of tools the model may call. Currently, only functions are
         *                   supported as a tool.
         * @param toolChoice Controls which (if any) function is called by the model.
         */
        public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, List<FunctionTool> tools,
                                     Object toolChoice) {
            this(messages, model, null, null, 1.0, null, null, null, null, false, tools, toolChoice);
        }

        /**
         * Shortcut constructor for a chat completion request with the given messages and
         * stream.
         */
        public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
            this(messages, DEFAULT_CHAT_MODEL, null, 0.7, 1.0, null, null, null, null, stream, null, null);
        }

        // Getters
        public List<ChatCompletionMessage> getMessages() {
            return messages;
        }

        public String getModel() {
            return model;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public Double getTemperature() {
            return temperature;
        }

        public Double getTopP() {
            return topP;
        }

        public Integer getN() {
            return n;
        }

        public Double getFrequencyPenalty() {
            return frequencyPenalty;
        }

        public Double getPresencePenalty() {
            return presencePenalty;
        }

        public List<String> getStop() {
            return stop;
        }

        public Boolean getStream() {
            return stream;
        }

        public List<FunctionTool> getTools() {
            return tools;
        }

        public Object getToolChoice() {
            return toolChoice;
        }

        /**
         * Helper factory that creates a tool_choice of type 'none', 'auto' or selected
         * function by name.
         */
        public static class ToolChoiceBuilder {

            /**
             * Model can pick between generating a message or calling a function.
             */
            public static final String AUTO = "auto";

            /**
             * Model will not call a function and instead generates a message
             */
            public static final String NONE = "none";

            /**
             * Specifying a particular function forces the model to call that function.
             */
            public static Object function(String functionName) {
                Map<String, Object> functionMap = new HashMap<>();
                functionMap.put("name", functionName);

                Map<String, Object> result = new HashMap<>();
                result.put("type", "function");
                result.put("function", functionMap);

                return result;
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletion {

        @JsonProperty("id")
        private String id;

        @JsonProperty("object")
        private String object;

        @JsonProperty("created")
        private Long created;

        @JsonProperty("model")
        private String model;

        @JsonProperty("choices")
        private List<Choice> choices;

        @JsonProperty("usage")
        private Usage usage;

        // 构造器
        public ChatCompletion(String id, String object, Long created, String model, List<Choice> choices, Usage usage) {
            this.id = id;
            this.object = object;
            this.created = created;
            this.model = model;
            this.choices = choices;
            this.usage = usage;
        }

        // getter & setter
        public String getId() {
            return id;
        }

        public String getObject() {
            return object;
        }

        public Long getCreated() {
            return created;
        }

        public String getModel() {
            return model;
        }

        public List<Choice> getChoices() {
            return choices;
        }

        public Usage getUsage() {
            return usage;
        }

    }

    // 内部类 Choice
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Choice {

        @JsonProperty("index")
        private Integer index;

        @JsonProperty("message")
        private ChatCompletionMessage message;

        @JsonProperty("finish_reason")
        private ChatCompletionFinishReason finishReason;

        @JsonProperty("usage")
        private Usage usage;

        public Choice(Integer index, ChatCompletionMessage message, ChatCompletionFinishReason finishReason, Usage usage) {
            this.index = index;
            this.message = message;
            this.finishReason = finishReason;
            this.usage = usage;
        }

        public Integer getIndex() {
            return index;
        }

        public ChatCompletionMessage getMessage() {
            return message;
        }

        public ChatCompletionFinishReason getFinishReason() {
            return finishReason;
        }

        public Usage getUsage() {
            return usage;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {
        // @formatter:off
        @JsonProperty("prompt_tokens")
        Integer promptTokens;
        @JsonProperty("total_tokens")
        Integer totalTokens;
        @JsonProperty("completion_tokens")
        Integer completionTokens;
        // @formatter:on

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
        }
    }

    /**
     * Chat completion message.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletionMessage {

        @JsonProperty("content")
        private Object rawContent;

        @JsonProperty("role")
        private Role role;

        @JsonProperty("name")
        private String name;

        @JsonProperty("tool_call_id")
        private String toolCallId;

        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;

        public ChatCompletionMessage() {
        }

        // Full constructor
        public ChatCompletionMessage(Object rawContent, Role role, String name,
                                     String toolCallId, List<ToolCall> toolCalls) {
            this.rawContent = rawContent;
            this.role = role;
            this.name = name;
            this.toolCallId = toolCallId;
            this.toolCalls = toolCalls;
        }

        /**
         * Create a chat completion message with the given content and role. All other
         * fields are null.
         *
         * @param content The contents of the message.
         * @param role    The role of the author of this message.
         */
        public ChatCompletionMessage(Object content, Role role) {
            this(content, role, null, null, null);
        }

        // Getters
        public Object getRawContent() {
            return rawContent;
        }

        public Role getRole() {
            return role;
        }

        public String getName() {
            return name;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        /**
         * Get message content as String.
         */
        public String content() {
            if (this.rawContent == null) {
                return null;
            }
            if (this.rawContent instanceof String) {
                return (String) this.rawContent;
            }
            throw new IllegalStateException("The content is not a string!");
        }


    }

    /**
     * The role of the author of this message. NOTE: Moonshot expects the system
     * message to be before the user message or will fail with 400 error.
     */
    public enum Role {
        /**
         * System message.
         */
        @JsonProperty("system")
        SYSTEM,
        /**
         * User message.
         */
        @JsonProperty("user")
        USER,
        /**
         * Assistant message.
         */
        @JsonProperty("assistant")
        ASSISTANT,
        /**
         * Tool message.
         */
        @JsonProperty("tool")
        TOOL
    }

    /**
     * The relevant tool call.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCall {

        @JsonProperty("id")
        private final String id;

        @JsonProperty("type")
        private final String type;

        @JsonProperty("function")
        private final ChatCompletionFunction function;

        /**
         * Constructor for ToolCall.
         *
         * @param id       The ID of the tool call. This ID must be referenced when you submit
         *                 the tool outputs in using the Submit tool outputs to run endpoint.
         * @param type     The type of tool call the output is required for. For now, this is
         *                 always function.
         * @param function The function definition.
         */
        public ToolCall(String id, String type, ChatCompletionFunction function) {
            this.id = id;
            this.type = type;
            this.function = function;
        }

        // Getters
        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public ChatCompletionFunction getFunction() {
            return function;
        }

    }

    /**
     * The function definition.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletionFunction {

        @JsonProperty("name")
        private final String name;

        @JsonProperty("arguments")
        private final String arguments;

        /**
         * Constructor for ChatCompletionFunction.
         *
         * @param name      The name of the function.
         * @param arguments The arguments that the model expects you to pass to the
         *                  function.
         */
        public ChatCompletionFunction(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        // Getters
        public String getName() {
            return name;
        }

        public String getArguments() {
            return arguments;
        }

    }

    /**
     * The reason the model stopped generating tokens.
     */
    public enum ChatCompletionFinishReason {

        /**
         * The model hit a natural stop point or a provided stop sequence.
         */
        @JsonProperty("stop")
        STOP,
        /**
         * The maximum number of tokens specified in the request was reached.
         */
        @JsonProperty("length")
        LENGTH,
        /**
         * The content was omitted due to a flag from our content filters.
         */
        @JsonProperty("content_filter")
        CONTENT_FILTER,
        /**
         * The model called a tool.
         */
        @JsonProperty("tool_calls")
        TOOL_CALLS,
        /**
         * Only for compatibility with Mistral AI API.
         */
        @JsonProperty("tool_call")
        TOOL_CALL

    }

    /**
     * Chat completion chunk for streaming responses.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletionChunk {

        @JsonProperty("id")
        private String id;

        @JsonProperty("object")
        private String object;

        @JsonProperty("created")
        private Long created;

        @JsonProperty("model")
        private String model;

        @JsonProperty("choices")
        private List<ChunkChoice> choices;

        public ChatCompletionChunk() {
        }

        // Constructor
        public ChatCompletionChunk(String id, String object, Long created, String model, List<ChunkChoice> choices) {
            this.id = id;
            this.object = object;
            this.created = created;
            this.model = model;
            this.choices = choices;
        }

        // Getters
        public String getId() {
            return id;
        }

        public String getObject() {
            return object;
        }

        public Long getCreated() {
            return created;
        }

        public String getModel() {
            return model;
        }

        public List<ChunkChoice> getChoices() {
            return choices;
        }

        /**
         * Chat completion choice.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class ChunkChoice {

            @JsonProperty("index")
            private Integer index;

            @JsonProperty("delta")
            private ChatCompletionMessage delta;

            @JsonProperty("finish_reason")
            private ChatCompletionFinishReason finishReason;

            @JsonProperty("usage")
            private Usage usage;

            public ChunkChoice() {

            }

            /**
             * Constructor for ChunkChoice.
             *
             * @param index        The index of the choice in the list of choices.
             * @param delta        A chat completion delta generated by streamed model responses.
             * @param finishReason The reason the model stopped generating tokens.
             * @param usage        Usage statistics for the completion request.
             */
            public ChunkChoice(Integer index, ChatCompletionMessage delta,
                               ChatCompletionFinishReason finishReason, Usage usage) {
                this.index = index;
                this.delta = delta;
                this.finishReason = finishReason;
                this.usage = usage;
            }

            // Getters
            public Integer getIndex() {
                return index;
            }

            public ChatCompletionMessage getDelta() {
                return delta;
            }

            public ChatCompletionFinishReason getFinishReason() {
                return finishReason;
            }

            public Usage getUsage() {
                return usage;
            }

        }
    }

    /**
     * Function tool definition.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionTool {

        @JsonProperty("type")
        private final Type type;

        @JsonProperty("function")
        private final Function function;

        // Full constructor
        public FunctionTool(Type type, Function function) {
            this.type = type;
            this.function = function;
        }

        /**
         * Create a tool of type 'function' and the given function definition.
         *
         * @param function function definition.
         */
        public FunctionTool(Function function) {
            this(Type.FUNCTION, function);
        }

        // Getters
        public Type getType() {
            return type;
        }

        public Function getFunction() {
            return function;
        }

        /**
         * Function tool type.
         */
        public enum Type {
            /**
             * Function tool type.
             */
            @JsonProperty("function")
            FUNCTION
        }

        /**
         * Function definition.
         */
        public static class Function {

            @JsonProperty("description")
            private String description;

            @JsonProperty("name")
            private String name;

            @JsonProperty("parameters")
            private Map<String, Object> parameters;

            /**
             * Create function definition.
             *
             * @param description A description of what the function does, used by the model
             *                    to choose when and how to call the function.
             * @param name        The name of the function to be called. Must be a-z, A-Z, 0-9, or
             *                    contain underscores and dashes, with a maximum length of 64.
             * @param parameters  The parameters the functions accepts, described as a JSON
             *                    Schema object. To describe a function that accepts no parameters, provide the
             *                    value {"type": "object", "properties": {}}.
             */
            public Function(String description, String name, Map<String, Object> parameters) {
                this.description = description;
                this.name = name;
                this.parameters = parameters;
            }

            /**
             * Create tool function definition.
             *
             * @param description tool function description.
             * @param name        tool function name.
             * @param jsonSchema  tool function schema as json.
             */
            public Function(String description, String name, String jsonSchema) {
                this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema));
            }

            // Getters
            public String getDescription() {
                return description;
            }

            public String getName() {
                return name;
            }

            public Map<String, Object> getParameters() {
                return parameters;
            }

        }
    }

    /**
     * Options for Moonshot chat completions.
     *
     * @author Geng Rong
     * @author Thomas Vitale
     * @author Alexandros Pappas
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MoonshotChatOptions implements ChatOptions {

        /**
         * ID of the model to use
         */
        private @JsonProperty("model")
        String model;

        /**
         * The maximum number of tokens to generate in the chat completion. The total length
         * of input tokens and generated tokens is limited by the model's context length.
         */
        private @JsonProperty("max_tokens")
        Integer maxTokens;

        /**
         * What sampling temperature to use, between 0.0 and 1.0. Higher values like 0.8 will
         * make the output more random, while lower values like 0.2 will make it more focused
         * and deterministic. We generally recommend altering this or top_p but not both.
         */
        private @JsonProperty("temperature")
        Double temperature;

        /**
         * An alternative to sampling with temperature, called nucleus sampling, where the
         * model considers the results of the tokens with top_p probability mass. So 0.1 means
         * only the tokens comprising the top 10% probability mass are considered. We
         * generally recommend altering this or temperature but not both.
         */
        private @JsonProperty("top_p")
        Double topP;

        /**
         * How many chat completion choices to generate for each input message. Note that you
         * will be charged based on the number of generated tokens across all the choices.
         * Keep n as 1 to minimize costs.
         */
        private @JsonProperty("n")
        Integer n;

        /**
         * Number between -2.0 and 2.0. Positive values penalize new tokens based on whether
         * they appear in the text so far, increasing the model's likelihood to talk about new
         * topics.
         */
        private @JsonProperty("presence_penalty")
        Double presencePenalty;

        /**
         * Number between -2.0 and 2.0. Positive values penalize new tokens based on their
         * existing frequency in the text so far, decreasing the model's likelihood to repeat
         * the same line verbatim.
         */
        private @JsonProperty("frequency_penalty")
        Double frequencyPenalty;

        /**
         * Up to 5 sequences where the API will stop generating further tokens.
         */
        private @JsonProperty("stop")
        List<String> stop;

        private @JsonProperty("tools")
        List<MoonshotApi.FunctionTool> tools;

        /**
         * Controls which (if any) function is called by the model. none means the model will
         * not call a function and instead generates a message. auto means the model can pick
         * between generating a message or calling a function. Specifying a particular
         * function via {"type: "function", "function": {"name": "my_function"}} forces the
         * model to call that function. none is the default when no functions are present.
         * auto is the default if functions are present. Use the
         * {@link MoonshotApi.ChatCompletionRequest.ToolChoiceBuilder} to create a tool choice
         * object.
         */
        private @JsonProperty("tool_choice")
        String toolChoice;

        /**
         * List of functions, identified by their names, to configure for function calling in
         * the chat completion requests. Functions with those names must exist in the
         * functionCallbacks registry. The {@link #functionCallbacks} from the PromptOptions
         * are automatically enabled for the duration of the prompt execution.
         * <p>
         * Note that function enabled with the default options are enabled for all chat
         * completion requests. This could impact the token count and the billing. If the
         * functions is set in a prompt options, then the enabled functions are only active
         * for the duration of this prompt execution.
         */
        @JsonIgnore
        private Set<String> functions = new HashSet<>();

        /**
         * A unique identifier representing your end-user, which can help Moonshot to monitor
         * and detect abuse.
         */
        private @JsonProperty("user")
        String user;

        @JsonIgnore
        private Boolean proxyToolCalls;

        @JsonIgnore
        private Map<String, Object> toolContext;

        public static Builder builder() {
            return new Builder();
        }

        public Set<String> getFunctions() {
            return this.functions;
        }

        public void setFunctions(Set<String> functionNames) {
            this.functions = functionNames;
        }

        public String getModel() {
            return this.model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Double getFrequencyPenalty() {
            return this.frequencyPenalty;
        }

        public void setFrequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
        }

        public Integer getMaxTokens() {
            return this.maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Integer getN() {
            return this.n;
        }

        public void setN(Integer n) {
            this.n = n;
        }

        public Double getPresencePenalty() {
            return this.presencePenalty;
        }

        public void setPresencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
        }

        @JsonIgnore
        public List<String> getStopSequences() {
            return getStop();
        }

        @JsonIgnore
        public void setStopSequences(List<String> stopSequences) {
            setStop(stopSequences);
        }

        public List<String> getStop() {
            return this.stop;
        }

        public void setStop(List<String> stop) {
            this.stop = stop;
        }

        public Double getTemperature() {
            return this.temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Double getTopP() {
            return this.topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }

        public String getUser() {
            return this.user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        @JsonIgnore
        public Integer getTopK() {
            return null;
        }

        public Boolean getProxyToolCalls() {
            return this.proxyToolCalls;
        }

        public void setProxyToolCalls(Boolean proxyToolCalls) {
            this.proxyToolCalls = proxyToolCalls;
        }

        public Map<String, Object> getToolContext() {
            return this.toolContext;
        }

        public void setToolContext(Map<String, Object> toolContext) {
            this.toolContext = toolContext;
        }

        public MoonshotChatOptions copy() {
            return builder().model(this.model)
                    .maxTokens(this.maxTokens)
                    .temperature(this.temperature)
                    .topP(this.topP)
                    .N(this.n)
                    .presencePenalty(this.presencePenalty)
                    .frequencyPenalty(this.frequencyPenalty)
                    .stop(this.stop)
                    .user(this.user)
                    .tools(this.tools)
                    .toolChoice(this.toolChoice)
                    .functions(this.functions)
                    .proxyToolCalls(this.proxyToolCalls)
                    .toolContext(this.toolContext)
                    .build();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((this.model == null) ? 0 : this.model.hashCode());
            result = prime * result + ((this.frequencyPenalty == null) ? 0 : this.frequencyPenalty.hashCode());
            result = prime * result + ((this.maxTokens == null) ? 0 : this.maxTokens.hashCode());
            result = prime * result + ((this.n == null) ? 0 : this.n.hashCode());
            result = prime * result + ((this.presencePenalty == null) ? 0 : this.presencePenalty.hashCode());
            result = prime * result + ((this.stop == null) ? 0 : this.stop.hashCode());
            result = prime * result + ((this.temperature == null) ? 0 : this.temperature.hashCode());
            result = prime * result + ((this.topP == null) ? 0 : this.topP.hashCode());
            result = prime * result + ((this.user == null) ? 0 : this.user.hashCode());
            result = prime * result + ((this.proxyToolCalls == null) ? 0 : this.proxyToolCalls.hashCode());
            result = prime * result + ((this.toolContext == null) ? 0 : this.toolContext.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            MoonshotChatOptions other = (MoonshotChatOptions) obj;
            if (this.model == null) {
                if (other.model != null) {
                    return false;
                }
            } else if (!this.model.equals(other.model)) {
                return false;
            }
            if (this.frequencyPenalty == null) {
                if (other.frequencyPenalty != null) {
                    return false;
                }
            } else if (!this.frequencyPenalty.equals(other.frequencyPenalty)) {
                return false;
            }
            if (this.maxTokens == null) {
                if (other.maxTokens != null) {
                    return false;
                }
            } else if (!this.maxTokens.equals(other.maxTokens)) {
                return false;
            }
            if (this.n == null) {
                if (other.n != null) {
                    return false;
                }
            } else if (!this.n.equals(other.n)) {
                return false;
            }
            if (this.presencePenalty == null) {
                if (other.presencePenalty != null) {
                    return false;
                }
            } else if (!this.presencePenalty.equals(other.presencePenalty)) {
                return false;
            }
            if (this.stop == null) {
                if (other.stop != null) {
                    return false;
                }
            } else if (!this.stop.equals(other.stop)) {
                return false;
            }
            if (this.temperature == null) {
                if (other.temperature != null) {
                    return false;
                }
            } else if (!this.temperature.equals(other.temperature)) {
                return false;
            }
            if (this.topP == null) {
                if (other.topP != null) {
                    return false;
                }
            } else if (!this.topP.equals(other.topP)) {
                return false;
            }
            if (this.user == null) {
                return other.user == null;
            } else if (!this.user.equals(other.user)) {
                return false;
            }
            if (this.proxyToolCalls == null) {
                return other.proxyToolCalls == null;
            } else if (!this.proxyToolCalls.equals(other.proxyToolCalls)) {
                return false;
            }
            if (this.toolContext == null) {
                return other.toolContext == null;
            } else if (!this.toolContext.equals(other.toolContext)) {
                return false;
            }
            return true;
        }

        public static class Builder {

            private final MoonshotChatOptions options = new MoonshotChatOptions();

            public Builder model(String model) {
                this.options.model = model;
                return this;
            }

            public Builder maxTokens(Integer maxTokens) {
                this.options.maxTokens = maxTokens;
                return this;
            }

            public Builder temperature(Double temperature) {
                this.options.temperature = temperature;
                return this;
            }

            public Builder topP(Double topP) {
                this.options.topP = topP;
                return this;
            }

            public Builder N(Integer n) {
                this.options.n = n;
                return this;
            }

            public Builder presencePenalty(Double presencePenalty) {
                this.options.presencePenalty = presencePenalty;
                return this;
            }

            public Builder frequencyPenalty(Double frequencyPenalty) {
                this.options.frequencyPenalty = frequencyPenalty;
                return this;
            }

            public Builder stop(List<String> stop) {
                this.options.stop = stop;
                return this;
            }

            public Builder user(String user) {
                this.options.user = user;
                return this;
            }

            public Builder tools(List<MoonshotApi.FunctionTool> tools) {
                this.options.tools = tools;
                return this;
            }

            public Builder toolChoice(String toolChoice) {
                this.options.toolChoice = toolChoice;
                return this;
            }

            public Builder functions(Set<String> functionNames) {
                Assert.notNull(functionNames, "Function names must not be null");
                this.options.functions = functionNames;
                return this;
            }

            public Builder function(String functionName) {
                Assert.hasText(functionName, "Function name must not be empty");
                if (this.options.functions == null) {
                    this.options.functions = new HashSet<>();
                }
                this.options.functions.add(functionName);
                return this;
            }

            public Builder proxyToolCalls(Boolean proxyToolCalls) {
                this.options.proxyToolCalls = proxyToolCalls;
                return this;
            }

            public Builder toolContext(Map<String, Object> toolContext) {
                if (this.options.toolContext == null) {
                    this.options.toolContext = toolContext;
                } else {
                    this.options.toolContext.putAll(toolContext);
                }
                return this;
            }

            public MoonshotChatOptions build() {
                return this.options;
            }

        }

    }

}