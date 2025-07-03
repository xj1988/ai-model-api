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

package com.github.aimodel.content;

import java.util.Map;

/**
 * 包含内容和元数据的数据结构。共同的父类
 * {@link org.springframework.ai.document.Document} 以及 org.springframework.ai.chat.messages 消息类。
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0.0
 */
public interface Content {

    /**
     * 获取消息的内容。
     *
     * @return the content of the message
     */
    String getText();

    /**
     * 获取与内容关联的元数据。
     *
     * @return the metadata associated with the content
     */
    Map<String, Object> getMetadata();

}