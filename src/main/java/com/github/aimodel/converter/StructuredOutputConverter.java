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

package com.github.aimodel.converter;

import org.springframework.core.convert.converter.Converter;

/**
 * 将（原始）LLM输出转换为类型的结构化响应。
 * {@link FormatProvider#getFormat()} 方法应提供所需格式的LLM提示描述。
 *
 * @param <T> 指定所需的响应类型。
 * @author Mark Pollack
 * @author Christian Tzolov
 */
public interface StructuredOutputConverter<T> extends Converter<String, T>, FormatProvider {

}
