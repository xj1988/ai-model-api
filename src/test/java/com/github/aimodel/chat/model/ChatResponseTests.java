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

package com.github.aimodel.chat.model;

import java.util.*;

import com.github.aimodel.chat.metadata.ChatGenerationMetadata;
import com.github.aimodel.chat.model.ChatResponse;
import com.github.aimodel.chat.messages.AssistantMessage;
import com.github.aimodel.chat.model.Generation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ChatResponse}.
 *
 * @author Thomas Vitale
 */
class ChatResponseTests {

    @Test
    void whenToolCallsArePresentThenReturnTrue() {
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(Collections.singletonList(new Generation(new AssistantMessage("", new HashMap<>(),
                        Collections.singletonList(new AssistantMessage.ToolCall("toolA", "function", "toolA", "{}"))))))
                .build();
        assertThat(chatResponse.hasToolCalls()).isTrue();
    }

    @Test
    void whenNoToolCallsArePresentThenReturnFalse() {
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(Collections.singletonList(new Generation(new AssistantMessage("Result"))))
                .build();
        assertThat(chatResponse.hasToolCalls()).isFalse();
    }

    @Test
    void whenFinishReasonIsNullThenThrow() {
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(Collections.singletonList(new Generation(new AssistantMessage("Result"),
                        ChatGenerationMetadata.builder().finishReason("completed").build())))
                .build();
        assertThatThrownBy(() -> chatResponse.hasFinishReasons(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finishReasons cannot be null");
    }

    @Test
    void whenFinishReasonIsPresent() {
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(Collections.singletonList(new Generation(new AssistantMessage("Result"),
                        ChatGenerationMetadata.builder().finishReason("completed").build())))
                .build();
        assertThat(chatResponse.hasFinishReasons(Collections.singleton("completed"))).isTrue();
    }

    @Test
    void whenFinishReasonIsNotPresent() {
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(Collections.singletonList(new Generation(new AssistantMessage("Result"),
                        ChatGenerationMetadata.builder().finishReason("failed").build())))
                .build();
        assertThat(chatResponse.hasFinishReasons(Collections.singleton("completed"))).isFalse();
    }

}