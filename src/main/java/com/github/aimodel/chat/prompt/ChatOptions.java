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

package com.github.aimodel.chat.prompt;

import java.util.List;

import com.github.aimodel.model.ModelOptions;
import org.springframework.lang.Nullable;

/**
 * {@link ModelOptions} 表示可跨不同聊天模型移植的常见选项。
 */
public interface ChatOptions extends ModelOptions {

    /**
     * 返回用于聊天的模型。
     *
     * @return the model to use for the chat
     */
    @Nullable
    String getModel();

    /**
     * 返回用于聊天的频率惩罚。
     *
     * @return the frequency penalty to use for the chat
     */
    @Nullable
    Double getFrequencyPenalty();

    /**
     * 返回用于聊天的最大令牌数。
     *
     * @return the maximum number of tokens to use for the chat
     */
    @Nullable
    Integer getMaxTokens();

    /**
     * 返回用于聊天的在线状态惩罚。
     *
     * @return the presence penalty to use for the chat
     */
    @Nullable
    Double getPresencePenalty();

    /**
     * 返回用于聊天的停止序列。
     *
     * @return the stop sequences to use for the chat
     */
    @Nullable
    List<String> getStopSequences();

    /**
     * 返回聊天时使用的温度。
     *
     * @return the temperature to use for the chat
     */
    @Nullable
    Double getTemperature();

    /**
     * 返回用于聊天的前K。
     *
     * @return the top K to use for the chat
     */
    @Nullable
    Integer getTopK();

    /**
     * 返回用于聊天的前P。
     *
     * @return the top P to use for the chat
     */
    @Nullable
    Double getTopP();

    /**
     * 返回此内容的副本 {@link ChatOptions}。
     *
     * @return a copy of this {@link ChatOptions}
     */
    <T extends ChatOptions> T copy();

    /**
     * 创建新 {@link Builder} 创建默认值 {@link ChatOptions}。
     *
     * @return Returns a new {@link Builder}.
     */
    static Builder builder() {
        return new DefaultChatOptionsBuilder();
    }

    /**
     * 生成器用于创建 {@link ChatOptions} 实例.
     */
    interface Builder {

        /**
         * Builds with the model to use for the chat.
         *
         * @param model
         * @return the builder
         */
        Builder model(String model);

        /**
         * Builds with the frequency penalty to use for the chat.
         *
         * @param frequencyPenalty
         * @return the builder.
         */
        Builder frequencyPenalty(Double frequencyPenalty);

        /**
         * Builds with the maximum number of tokens to use for the chat.
         *
         * @param maxTokens
         * @return the builder.
         */
        Builder maxTokens(Integer maxTokens);

        /**
         * Builds with the presence penalty to use for the chat.
         *
         * @param presencePenalty
         * @return the builder.
         */
        Builder presencePenalty(Double presencePenalty);

        /**
         * Builds with the stop sequences to use for the chat.
         *
         * @param stopSequences
         * @return the builder.
         */
        Builder stopSequences(List<String> stopSequences);

        /**
         * Builds with the temperature to use for the chat.
         *
         * @param temperature
         * @return the builder.
         */
        Builder temperature(Double temperature);

        /**
         * Builds with the top K to use for the chat.
         *
         * @param topK
         * @return the builder.
         */
        Builder topK(Integer topK);

        /**
         * Builds with the top P to use for the chat.
         *
         * @param topP
         * @return the builder.
         */
        Builder topP(Double topP);

        /**
         * Build the {@link ChatOptions}.
         *
         * @return the Chat options.
         */
        ChatOptions build();

    }

}