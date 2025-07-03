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

package com.github.aimodel.chat.messages;

import java.util.*;

import com.github.aimodel.content.MediaContent;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * 让生成者知道内容是作为对用户的响应而生成的。此角色表示生成者之前在对话中生成的消息。通过在系列中包含辅助信息，你可以为对话中关于先前交流的生成提供上下文。
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class AssistantMessage extends AbstractMessage implements MediaContent {

    private final List<ToolCall> toolCalls;

    protected final List<Media> media;

    public AssistantMessage(String content) {
        this(content, new HashMap<>());
    }

    public AssistantMessage(String content, Map<String, Object> properties) {
        this(content, properties, new ArrayList<>());
    }

    public AssistantMessage(String content, Map<String, Object> properties, List<ToolCall> toolCalls) {
        this(content, properties, toolCalls, new ArrayList<>());
    }

    public AssistantMessage(String content, Map<String, Object> properties, List<ToolCall> toolCalls,
                            List<Media> media) {
        super(MessageType.ASSISTANT, content, properties);
        Assert.notNull(toolCalls, "Tool calls must not be null");
        Assert.notNull(media, "Media must not be null");
        this.toolCalls = toolCalls;
        this.media = media;
    }

    public List<ToolCall> getToolCalls() {
        return this.toolCalls;
    }

    public boolean hasToolCalls() {
        return !CollectionUtils.isEmpty(this.toolCalls);
    }

    @Override
    public List<Media> getMedia() {
        return this.media;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AssistantMessage)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AssistantMessage that = (AssistantMessage) o;
        return Objects.equals(this.toolCalls, that.toolCalls) && Objects.equals(this.media, that.media);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.toolCalls, this.media);
    }

    @Override
    public String toString() {
        return "AssistantMessage [messageType=" + this.messageType + ", toolCalls=" + this.toolCalls + ", textContent="
                + this.textContent + ", metadata=" + this.metadata + "]";
    }

    public static class ToolCall {
        private final String id;
        private final String type;
        private final String name;
        private final String arguments;

        public ToolCall(String id, String type, String name, String arguments) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.arguments = arguments;
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getArguments() {
            return arguments;
        }

    }

}