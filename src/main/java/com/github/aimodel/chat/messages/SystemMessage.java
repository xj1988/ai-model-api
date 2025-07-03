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

package com.github.aimodel.chat.messages;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import com.github.aimodel.util.MessageUtils;

/**
 * 作为输入传递的“system”类型的消息。系统消息为对话提供了高级指令。该角色通常为对话提供高级指导。
 * 例如，你可以使用系统消息来指示生成器表现得像某个字符，或者以特定的格式提供答案。
 */
public class SystemMessage extends AbstractMessage {

    public SystemMessage(String textContent) {
        this(textContent, new HashMap<>());
    }

    public SystemMessage(Resource resource) {
        this(MessageUtils.readResource(resource), new HashMap<>());
    }

    private SystemMessage(String textContent, Map<String, Object> metadata) {
        super(MessageType.SYSTEM, textContent, metadata);
    }

    @Override
    @NonNull
    public String getText() {
        return this.textContent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SystemMessage)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        SystemMessage that = (SystemMessage) o;
        return Objects.equals(this.textContent, that.textContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.textContent);
    }

    @Override
    public String toString() {
        return "SystemMessage{" + "textContent='" + this.textContent + '\'' + ", messageType=" + this.messageType
                + ", metadata=" + this.metadata + '}';
    }

    public SystemMessage copy() {
        return new SystemMessage(getText(), new HashMap<>(this.metadata));
    }

    public Builder mutate() {
        return new Builder().text(this.textContent).metadata(this.metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        @Nullable
        private String textContent;

        @Nullable
        private Resource resource;

        private Map<String, Object> metadata = new HashMap<>();

        public Builder text(String textContent) {
            this.textContent = textContent;
            return this;
        }

        public Builder text(Resource resource) {
            this.resource = resource;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public SystemMessage build() {
            if (StringUtils.hasText(this.textContent) && this.resource != null) {
                throw new IllegalArgumentException("textContent and resource cannot be set at the same time");
            } else if (this.resource != null) {
                this.textContent = MessageUtils.readResource(this.resource);
            }
            return new SystemMessage(this.textContent, this.metadata);
        }

    }

}