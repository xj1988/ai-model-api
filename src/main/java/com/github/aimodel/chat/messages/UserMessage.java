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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.aimodel.content.MediaContent;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.github.aimodel.util.MessageUtils;

/**
 * 作为输入传递的“user”类型的消息。具有用户角色的消息来自用户或开发人员。它们代表问题、提示或您希望生成者回应的任何输入。
 */
public class UserMessage extends AbstractMessage implements MediaContent {

    protected List<Media> media;

    public UserMessage(String textContent) {
        this(textContent, new ArrayList<>(), new HashMap<>());
    }

    private UserMessage(String textContent, Collection<Media> media, Map<String, Object> metadata) {
        super(MessageType.USER, textContent, metadata);
        Assert.notNull(media, "media cannot be null");
        Assert.noNullElements(media, "media cannot have null elements");
        this.media = new ArrayList<>(media);
    }

    public UserMessage(Resource resource) {
        this(MessageUtils.readResource(resource));
    }

    @Override
    public String toString() {
        return "UserMessage{" + "content='" + getText() + '\'' + ", properties=" + this.metadata + ", messageType="
                + this.messageType + '}';
    }

    @Override
    @NonNull
    public String getText() {
        return this.textContent;
    }

    @Override
    public List<Media> getMedia() {
        return this.media;
    }

    public UserMessage copy() {
        return new Builder().text(getText()).media(new ArrayList<>(getMedia())).metadata(new HashMap<>(getMetadata())).build();
    }

    public Builder mutate() {
        return new Builder().text(getText()).media(new ArrayList<>(getMedia())).metadata(new HashMap<>(getMetadata()));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        @Nullable
        private String textContent;

        @Nullable
        private Resource resource;

        private List<Media> media = new ArrayList<>();

        private Map<String, Object> metadata = new HashMap<>();

        public Builder text(String textContent) {
            this.textContent = textContent;
            return this;
        }

        public Builder text(Resource resource) {
            this.resource = resource;
            return this;
        }

        public Builder media(List<Media> media) {
            this.media = media;
            return this;
        }

        public Builder media(@Nullable Media... media) {
            if (media != null) {
                this.media = Arrays.asList(media);
            }
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public UserMessage build() {
            if (StringUtils.hasText(this.textContent) && this.resource != null) {
                throw new IllegalArgumentException("textContent and resource cannot be set at the same time");
            } else if (this.resource != null) {
                this.textContent = MessageUtils.readResource(this.resource);
            }
            return new UserMessage(this.textContent, this.media, this.metadata);
        }

    }

}