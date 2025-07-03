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
 * 模型接口提供了一个通用的API，用于调用人工智能模型。它旨在通过抽象发送请求和接收响应的过程来处理与各种类型的AI模型的交互。
 * 该接口使用Java泛型来适应不同类型的请求和响应，增强了跨不同AI模型实现的灵活性和适应性。
 *
 * @param <TReq> the generic type of the request to the AI model
 * @param <TRes> the generic type of the response from the AI model
 * @author Mark Pollack
 * @since 0.8.0
 */
public interface Model<TReq extends ModelRequest<?>, TRes extends ModelResponse<?>> {

    /**
     * 执行对AI模型的方法调用。
     *
     * @param request 要发送到AI模型的请求对象
     * @return the response from the AI model
     */
    TRes call(TReq request);

}
