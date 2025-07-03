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

package com.github.aimodel.chat.metadata;

import java.util.Map;
import java.util.Objects;

import com.github.aimodel.model.AbstractResponseMetadata;
import com.github.aimodel.model.ResponseMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 对AI响应中返回的通用AI提供者元数据进行建模。
 *
 * @author John Blum
 * @author Thomas Vitale
 * @author Mark Pollack
 * @author Alexandros Pappas
 * @since 1.0.0
 */
public class ChatResponseMetadata extends AbstractResponseMetadata implements ResponseMetadata {

    private static final Logger logger = LoggerFactory.getLogger(ChatResponseMetadata.class);

    private String id = ""; // Set to blank to preserve backward compat with previous

    // interface default methods

    private String model = "";

    private RateLimit rateLimit = new EmptyRateLimit();

    private Usage usage = new EmptyUsage();

    private PromptMetadata promptMetadata = PromptMetadata.empty();

    public static Builder builder() {
        return new Builder();
    }

    /**
     * A unique identifier for the chat completion operation.
     *
     * @return unique operation identifier.
     */
    public String getId() {
        return this.id;
    }

    /**
     * The model that handled the request.
     *
     * @return the model that handled the request.
     */
    public String getModel() {
        return this.model;
    }

    /**
     * Returns AI provider specific metadata on rate limits.
     *
     * @return AI provider specific metadata on rate limits.
     * @see RateLimit
     */
    public RateLimit getRateLimit() {
        return this.rateLimit;
    }

    /**
     * Returns AI provider specific metadata on API usage.
     *
     * @return AI provider specific metadata on API usage.
     * @see Usage
     */
    public Usage getUsage() {
        return this.usage;
    }

    /**
     * Returns the prompt metadata gathered by the AI during request processing.
     *
     * @return the prompt metadata.
     */
    public PromptMetadata getPromptMetadata() {
        return this.promptMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatResponseMetadata)) {
            return false;
        }
        ChatResponseMetadata that = (ChatResponseMetadata) o;
        return Objects.equals(this.id, that.id) && Objects.equals(this.model, that.model)
                && Objects.equals(this.rateLimit, that.rateLimit) && Objects.equals(this.usage, that.usage)
                && Objects.equals(this.promptMetadata, that.promptMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.model, this.rateLimit, this.usage, this.promptMetadata);
    }

    @Override
    public String toString() {
        return String.format(AI_METADATA_STRING, getId(), getUsage(), getRateLimit());
    }

    public static class Builder {

        private final ChatResponseMetadata chatResponseMetadata;

        public Builder() {
            this.chatResponseMetadata = new ChatResponseMetadata();
        }

        public Builder metadata(Map<String, Object> mapToCopy) {
            this.chatResponseMetadata.map.putAll(mapToCopy);
            return this;
        }

        public Builder keyValue(String key, Object value) {
            if (key == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            if (value != null) {
                this.chatResponseMetadata.map.put(key, value);
            } else {
                logger.debug("Ignore null value for key [{}]", key);
            }
            return this;
        }

        public Builder id(String id) {
            this.chatResponseMetadata.id = id;
            return this;
        }

        public Builder model(String model) {
            this.chatResponseMetadata.model = model;
            return this;
        }

        public Builder rateLimit(RateLimit rateLimit) {
            this.chatResponseMetadata.rateLimit = rateLimit;
            return this;
        }

        public Builder usage(Usage usage) {
            this.chatResponseMetadata.usage = usage;
            return this;
        }

        public Builder promptMetadata(PromptMetadata promptMetadata) {
            this.chatResponseMetadata.promptMetadata = promptMetadata;
            return this;
        }

        public ChatResponseMetadata build() {
            return this.chatResponseMetadata;
        }

    }

}