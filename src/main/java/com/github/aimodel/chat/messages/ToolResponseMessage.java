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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ToolResponseMessage类表示聊天应用程序中具有函数内容的消息。
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class ToolResponseMessage extends AbstractMessage {

    protected final List<ToolResponse> responses;

    public ToolResponseMessage(List<ToolResponse> responses) {
        this(responses, new HashMap<>());
    }

    public ToolResponseMessage(List<ToolResponse> responses, Map<String, Object> metadata) {
        super(MessageType.TOOL, "", metadata);
        this.responses = responses;
    }

    public List<ToolResponse> getResponses() {
        return this.responses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ToolResponseMessage)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ToolResponseMessage that = (ToolResponseMessage) o;
        return Objects.equals(this.responses, that.responses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.responses);
    }

    @Override
    public String toString() {
        return "ToolResponseMessage{" + "responses=" + this.responses + ", messageType=" + this.messageType
                + ", metadata=" + this.metadata + '}';
    }

    public static class ToolResponse {
        private final String id;
        private final String name;
        private final String responseData;

        public ToolResponse(String id, String name, String responseData) {
            this.id = id;
            this.name = name;
            this.responseData = responseData;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getResponseData() {
            return responseData;
        }
    }

}