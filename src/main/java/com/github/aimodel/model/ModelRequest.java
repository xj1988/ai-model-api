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

package com.github.aimodel.model;

/**
 * 表示对AI模型的请求的接口。此接口封装了与AI模型交互所需的必要信息，包括指令或输入和其他模型选项。
 * 它提供了一种向人工智能模型发送请求的标准化方式，确保包括所有必要的细节，并且可以轻松管理。
 *
 * @param <T> the type of instructions or input required by the AI model
 * @author Mark Pollack
 * @since 0.8.0
 */
public interface ModelRequest<T> {

    /**
     * 检索AI模型所需的指令或输入。
     *
     * @return the instructions or input required by the AI model
     */
    T getInstructions();

    /**
     * 检索AI模型交互的可自定义选项。
     *
     * @return the customizable options for AI model interactions
     */
    ModelOptions getOptions();

}