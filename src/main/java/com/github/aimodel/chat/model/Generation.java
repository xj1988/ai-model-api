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

import com.github.aimodel.chat.metadata.ChatGenerationMetadata;
import com.github.aimodel.chat.messages.AssistantMessage;
import com.github.aimodel.model.ModelResult;

import java.util.Objects;


/**
 * AI返回的响应。
 */
public class Generation implements ModelResult<AssistantMessage> {

    private final AssistantMessage assistantMessage;

    private ChatGenerationMetadata chatGenerationMetadata;

    public Generation(AssistantMessage assistantMessage) {
        this(assistantMessage, ChatGenerationMetadata.NULL);
    }

    public Generation(AssistantMessage assistantMessage, ChatGenerationMetadata chatGenerationMetadata) {
        this.assistantMessage = assistantMessage;
        this.chatGenerationMetadata = chatGenerationMetadata;
    }

    @Override
    public AssistantMessage getOutput() {
        return this.assistantMessage;
    }

    @Override
    public ChatGenerationMetadata getMetadata() {
        ChatGenerationMetadata chatGenerationMetadata = this.chatGenerationMetadata;
        return chatGenerationMetadata != null ? chatGenerationMetadata : ChatGenerationMetadata.NULL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Generation)) {
            return false;
        }
        Generation that = (Generation) o;
        return Objects.equals(this.assistantMessage, that.assistantMessage)
                && Objects.equals(this.chatGenerationMetadata, that.chatGenerationMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.assistantMessage, this.chatGenerationMetadata);
    }

    @Override
    public String toString() {
        return "Generation[" + "assistantMessage=" + this.assistantMessage + ", chatGenerationMetadata="
                + this.chatGenerationMetadata + ']';
    }

}