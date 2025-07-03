/*
 * Copyright 2023-2025 the original author or authors.
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

package com.github.aimodel.chat.prompt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.github.aimodel.chat.messages.*;
import com.github.aimodel.model.ModelRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Prompt类表示在AI模型请求中使用的提示。提示由一条或多条消息和其他聊天选项组成。
 *
 * @author Mark Pollack
 * @author luocongqiu
 * @author Thomas Vitale
 */
public class Prompt implements ModelRequest<List<Message>> {

    private final List<Message> messages;

    @Nullable
    private ChatOptions chatOptions;

    public Prompt(String contents) {
        this(new UserMessage(contents));
    }

    public Prompt(Message message) {
        this(Collections.singletonList(message));
    }

    public Prompt(List<Message> messages) {
        this(messages, null);
    }

    public Prompt(Message... messages) {
        this(Arrays.asList(messages), null);
    }

    public Prompt(String contents, @Nullable ChatOptions chatOptions) {
        this(new UserMessage(contents), chatOptions);
    }

    public Prompt(Message message, @Nullable ChatOptions chatOptions) {
        this(Collections.singletonList(message), chatOptions);
    }

    public Prompt(List<Message> messages, @Nullable ChatOptions chatOptions) {
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");
        this.messages = messages;
        this.chatOptions = chatOptions;
    }

    public String getContents() {
        StringBuilder sb = new StringBuilder();
        for (Message message : getInstructions()) {
            sb.append(message.getText());
        }
        return sb.toString();
    }

    @Override
    @Nullable
    public ChatOptions getOptions() {
        return this.chatOptions;
    }

    @Override
    public List<Message> getInstructions() {
        return this.messages;
    }

    /**
     * 获取提示中的第一条系统消息。如果没有找到系统消息，则返回空的SystemMessage。
     */
    public SystemMessage getSystemMessage() {
        for (int i = 0; i <= this.messages.size() - 1; i++) {
            Message message = this.messages.get(i);
            if (message instanceof SystemMessage) {
                return (SystemMessage) message;
            }
        }
        return new SystemMessage("");
    }

    /**
     * 获取提示中的最后一条用户消息。如果没有找到用户消息，则返回空的UserMessage。
     */
    public UserMessage getUserMessage() {
        for (int i = this.messages.size() - 1; i >= 0; i--) {
            Message message = this.messages.get(i);
            if (message instanceof UserMessage) {
                return (UserMessage) message;
            }
        }
        return new UserMessage("");
    }

    /**
     * 获取提示中的所有用户消息。
     *
     * @return a list of all user messages in the prompt
     */
    public List<UserMessage> getUserMessages() {
        List<UserMessage> userMessages = new ArrayList<>();
        for (Message message : this.messages) {
            if (message instanceof UserMessage) {
                userMessages.add((UserMessage) message);
            }
        }
        return userMessages;
    }

    @Override
    public String toString() {
        return "Prompt{" + "messages=" + this.messages + ", modelOptions=" + this.chatOptions + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Prompt)) {
            return false;
        }
        Prompt that = (Prompt) o;
        return Objects.equals(this.messages, that.messages) && Objects.equals(this.chatOptions, that.chatOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.messages, this.chatOptions);
    }

    public Prompt copy() {
        return new Prompt(instructionsCopy(), null == this.chatOptions ? null : this.chatOptions.copy());
    }

    private List<Message> instructionsCopy() {
        List<Message> messagesCopy = new ArrayList<>();
        this.messages.forEach(message -> {
            if (message instanceof UserMessage) {
                messagesCopy.add(((UserMessage) message).copy());
            } else if (message instanceof SystemMessage) {
                messagesCopy.add(((SystemMessage) message).copy());
            } else if (message instanceof AssistantMessage) {
                AssistantMessage that = (AssistantMessage) message;
                messagesCopy.add(new AssistantMessage(that.getText(), that.getMetadata(), that.getToolCalls()));
            } else if (message instanceof ToolResponseMessage) {
                ToolResponseMessage that = (ToolResponseMessage) message;
                messagesCopy.add(new ToolResponseMessage(new ArrayList<>(that.getResponses()), new HashMap<>(that.getMetadata())));
            } else {
                throw new IllegalArgumentException("Unsupported message type: " + message.getClass().getName());
            }
        });

        return messagesCopy;
    }

    /**
     * Augments the first system message in the prompt with the provided function. If no
     * system message is found, a new one is created with the provided text.
     *
     * @return a new {@link Prompt} instance with the augmented system message.
     */
    public Prompt augmentSystemMessage(Function<SystemMessage, SystemMessage> systemMessageAugmenter) {
        List<Message> messagesCopy = new ArrayList<>(this.messages);
        boolean found = false;
        for (int i = 0; i < messagesCopy.size(); i++) {
            Message message = messagesCopy.get(i);
            if (message instanceof SystemMessage) {
                messagesCopy.set(i, systemMessageAugmenter.apply((SystemMessage) message));
                found = true;
                break;
            }
        }
        if (!found) {
            // If no system message is found, create a new one with the provided text
            // and add it as the first item in the list.
            messagesCopy.add(0, systemMessageAugmenter.apply(new SystemMessage("")));
        }
        return new Prompt(messagesCopy, null == this.chatOptions ? null : this.chatOptions.copy());
    }

    /**
     * Augments the last system message in the prompt with the provided text. If no system
     * message is found, a new one is created with the provided text.
     *
     * @return a new {@link Prompt} instance with the augmented system message.
     */
    public Prompt augmentSystemMessage(String newSystemText) {
        return augmentSystemMessage(systemMessage -> systemMessage.mutate().text(newSystemText).build());
    }

    /**
     * Augments the last user message in the prompt with the provided function. If no user
     * message is found, a new one is created with the provided text.
     *
     * @return a new {@link Prompt} instance with the augmented user message.
     */
    public Prompt augmentUserMessage(Function<UserMessage, UserMessage> userMessageAugmenter) {
        List<Message> messagesCopy = new ArrayList<>(this.messages);
        for (int i = messagesCopy.size() - 1; i >= 0; i--) {
            Message message = messagesCopy.get(i);
            if (message instanceof UserMessage) {
                messagesCopy.set(i, userMessageAugmenter.apply((UserMessage) message));
                break;
            }
            if (i == 0) {
                messagesCopy.add(userMessageAugmenter.apply(new UserMessage("")));
            }
        }

        return new Prompt(messagesCopy, null == this.chatOptions ? null : this.chatOptions.copy());
    }

    /**
     * Augments the last user message in the prompt with the provided text. If no user
     * message is found, a new one is created with the provided text.
     *
     * @return a new {@link Prompt} instance with the augmented user message.
     */
    public Prompt augmentUserMessage(String newUserText) {
        return augmentUserMessage(userMessage -> userMessage.mutate().text(newUserText).build());
    }

    public Builder mutate() {
        Builder builder = new Builder().messages(instructionsCopy());
        if (this.chatOptions != null) {
            builder.chatOptions(this.chatOptions.copy());
        }
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        @Nullable
        private String content;

        @Nullable
        private List<Message> messages;

        @Nullable
        private ChatOptions chatOptions;

        public Builder content(@Nullable String content) {
            this.content = content;
            return this;
        }

        public Builder messages(Message... messages) {
            if (messages != null) {
                this.messages = Arrays.asList(messages);
            }
            return this;
        }

        public Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        public Builder chatOptions(ChatOptions chatOptions) {
            this.chatOptions = chatOptions;
            return this;
        }

        public Prompt build() {
            if (StringUtils.hasText(this.content) && !CollectionUtils.isEmpty(this.messages)) {
                throw new IllegalArgumentException("content and messages cannot be set at the same time");
            } else if (StringUtils.hasText(this.content)) {
                this.messages = new ArrayList<>();
                this.messages.add(new UserMessage(this.content));
            }
            return new Prompt(this.messages, this.chatOptions);
        }

    }

}