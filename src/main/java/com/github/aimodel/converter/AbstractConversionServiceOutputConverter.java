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

import org.springframework.core.convert.support.DefaultConversionService;

/**
 * 抽象 {@link StructuredOutputConverter} 使用预配置的实现
 * {@link DefaultConversionService} 将LLM输出转换为所需的类型格式。
 *
 * @param <T> 指定所需的响应类型。
 * @author Mark Pollack
 * @author Christian Tzolov
 */
public abstract class AbstractConversionServiceOutputConverter<T> implements StructuredOutputConverter<T> {

    private final DefaultConversionService conversionService;

    /**
     * Create a new {@link AbstractConversionServiceOutputConverter} instance.
     *
     * @param conversionService the {@link DefaultConversionService} 用于转换输出。
     */
    public AbstractConversionServiceOutputConverter(DefaultConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * 返回此转换器使用的ConversionService。
     *
     * @return 此转换器使用的ConversionService。
     */
    public DefaultConversionService getConversionService() {
        return this.conversionService;
    }

}
