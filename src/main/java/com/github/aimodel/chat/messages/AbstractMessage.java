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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

/**
 * AbstractMessage类是Message接口的抽象实现。它为消息内容、媒体附件、元数据和消息类型提供了基础实现。
 *
 * @see Message
 */
public abstract class AbstractMessage implements Message {

    /**
     * 元数据中消息类型的键。
     */
    public static final String MESSAGE_TYPE = "messageType";

    /**
     * 消息的消息类型。
     */
    protected final MessageType messageType;

    /**
     * 消息的内容。
     */
    @Nullable
    protected final String textContent;

    /**
     * 消息影响响应的其他选项，而不是生成映射。
     */
    protected final Map<String, Object> metadata;

    /**
     * 使用给定的消息类型、文本内容和元数据创建新的AbstractMessage。
     *
     * @param messageType the message type
     * @param textContent the text content
     * @param metadata    the metadata
     */
    protected AbstractMessage(MessageType messageType, @Nullable String textContent, Map<String, Object> metadata) {
        Assert.notNull(messageType, "Message type must not be null");
        if (messageType == MessageType.SYSTEM || messageType == MessageType.USER) {
            Assert.notNull(textContent, "Content must not be null for SYSTEM or USER messages");
        }
        Assert.notNull(metadata, "Metadata must not be null");
        this.messageType = messageType;
        this.textContent = textContent;
        this.metadata = new HashMap<>(metadata);
        this.metadata.put(MESSAGE_TYPE, messageType);
    }

    /**
     * Create a new AbstractMessage with the given message type, resource, and metadata.
     *
     * @param messageType the message type
     * @param resource    the resource
     * @param metadata    the metadata
     */
    protected AbstractMessage(MessageType messageType, Resource resource, Map<String, Object> metadata) {
        Assert.notNull(messageType, "Message type must not be null");
        Assert.notNull(resource, "Resource must not be null");
        Assert.notNull(metadata, "Metadata must not be null");
        try (InputStream inputStream = resource.getInputStream()) {
            this.textContent = StreamUtils.copyToString(inputStream, Charset.defaultCharset());
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read resource", ex);
        }
        this.messageType = messageType;
        this.metadata = new HashMap<>(metadata);
        this.metadata.put(MESSAGE_TYPE, messageType);
    }

    /**
     * Get the content of the message.
     *
     * @return the content of the message
     */
    @Override
    @Nullable
    public String getText() {
        return this.textContent;
    }

    /**
     * Get the metadata of the message.
     *
     * @return the metadata of the message
     */
    @Override
    public Map<String, Object> getMetadata() {
        return this.metadata;
    }

    /**
     * Get the message type of the message.
     *
     * @return the message type of the message
     */
    @Override
    public MessageType getMessageType() {
        return this.messageType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractMessage)) {
            return false;
        }

        AbstractMessage that = (AbstractMessage) o;
        return this.messageType == that.messageType && Objects.equals(this.textContent, that.textContent)
                && Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.messageType, this.textContent, this.metadata);
    }

}