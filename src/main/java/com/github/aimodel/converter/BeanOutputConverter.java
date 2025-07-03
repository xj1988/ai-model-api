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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;


/**
 * 实现 {@link StructuredOutputConverter} 转换LLM输出
 * 使用JSON模式转换为特定的对象类型。此转换器的工作原理是基于给定的Java类或参数化类型引用生成JSON模式，
 * 然后使用该模式*验证LLM输出并将其转换为所需的类型。
 *
 * @param <T> 输出将转换为的目标类型。
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Sebastian Ullrich
 * @author Kirk Lund
 * @author Josh Long
 * @author Sebastien Deleuze
 * @author Soby Chacko
 * @author Thomas Vitale
 */
public class BeanOutputConverter<T> implements StructuredOutputConverter<T> {

    protected final Logger logger = LoggerFactory.getLogger(BeanOutputConverter.class);

    /**
     * 输出将转换到的目标类类型引用。
     */
    protected final Type type;

    /**
     * 用于反序列化和其他JSON操作的对象映射器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 保存为目标类型生成的JSON模式。
     */
    protected String jsonSchema;

    /**
     * Constructor to initialize with the target type's class.
     *
     * @param clazz The target type's class.
     */
    public BeanOutputConverter(Class<T> clazz) {
        this(ParameterizedTypeReference.forType(clazz));
    }

    /**
     * Constructor to initialize with the target type's class, a custom object mapper, and
     * a line endings normalizer to ensure consistent line endings on any platform.
     *
     * @param clazz        The target type's class.
     * @param objectMapper Custom object mapper for JSON operations. endings.
     */
    public BeanOutputConverter(Class<T> clazz, ObjectMapper objectMapper) {
        this(ParameterizedTypeReference.forType(clazz), objectMapper);
    }

    /**
     * Constructor to initialize with the target class type reference.
     *
     * @param typeRef The target class type reference.
     */
    public BeanOutputConverter(ParameterizedTypeReference<T> typeRef) {
        this(typeRef.getType(), null);
    }

    /**
     * Constructor to initialize with the target class type reference, a custom object
     * mapper, and a line endings normalizer to ensure consistent line endings on any
     * platform.
     *
     * @param typeRef      The target class type reference.
     * @param objectMapper Custom object mapper for JSON operations. endings.
     */
    public BeanOutputConverter(ParameterizedTypeReference<T> typeRef, ObjectMapper objectMapper) {
        this(typeRef.getType(), objectMapper);
    }

    /**
     * Constructor to initialize with the target class type reference, a custom object
     * mapper, and a line endings normalizer to ensure consistent line endings on any
     * platform.
     *
     * @param type         The target class type.
     * @param objectMapper Custom object mapper for JSON operations. endings.
     */
    private BeanOutputConverter(Type type, ObjectMapper objectMapper) {
        Objects.requireNonNull(type, "Type cannot be null;");
        this.type = type;
        this.objectMapper = objectMapper != null ? objectMapper : getObjectMapper();
        generateSchema();
    }

    /**
     * 为目标类型生成JSON模式。
     */
    protected void generateSchema() {
        JacksonModule jacksonModule = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                JacksonOption.RESPECT_JSONPROPERTY_ORDER);
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_2020_12,
                com.github.victools.jsonschema.generator.OptionPreset.PLAIN_JSON)
                .with(jacksonModule)
                .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT);
        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);
        JsonNode jsonNode = generator.generateSchema(this.type);
        ObjectWriter objectWriter = this.objectMapper.writer(new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter().withLinefeed(System.lineSeparator())));
        try {
            this.jsonSchema = objectWriter.writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            logger.error("Could not pretty print json schema for jsonNode: {}", jsonNode);
            throw new RuntimeException("Could not pretty print json schema for " + this.type, e);
        }
    }

    /**
     * 解析给定的文本以将其转换为所需的目标类型。
     *
     * @param text LLM输出的字符串。
     * @return 所需目标类型的解析输出。
     */
    @SuppressWarnings("unchecked")
    @Override
    public T convert(@NonNull String text) {
        try {
            // Remove leading and trailing whitespace
            text = text.trim();

            // Check for and remove triple backticks and "json" identifier
            if (text.startsWith("```") && text.endsWith("```")) {
                // Remove the first line if it contains "```json"
                String[] lines = text.split("\n", 2);
                if (lines[0].trim().equalsIgnoreCase("```json")) {
                    text = lines.length > 1 ? lines[1] : "";
                } else {
                    text = text.substring(3); // Remove leading ```
                }

                // Remove trailing ```
                text = text.substring(0, text.length() - 3);

                // Trim again to remove any potential whitespace
                text = text.trim();
            }
            return (T) this.objectMapper.readValue(text, this.objectMapper.constructType(this.type));
        } catch (JsonProcessingException e) {
            logger.error("Could not parse the given text to the desired target type: \"{}\" into {}", text, this.type);
            throw new RuntimeException(e);
        }
    }

    /**
     * 为JSON操作配置并返回对象映射器。
     *
     * @return Configured object mapper.
     */
    protected ObjectMapper getObjectMapper() {
        return JsonMapper.builder()
                .addModules(instantiateAvailableModules())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /**
     * 实例化类路径中可用的、众所周知的Jackson模块。
     * <p>
     * 支持以下模块: <code>Jdk8Module</code>, <code>JavaTimeModule</code>,
     * <code>ParameterNamesModule</code> and <code>KotlinModule</code>.
     *
     * @return 实例化模块的列表。
     */
    @SuppressWarnings("unchecked")
    public static List<Module> instantiateAvailableModules() {
        List<Module> modules = new ArrayList<>();
        try {
            Class<? extends com.fasterxml.jackson.databind.Module> jdk8ModuleClass = (Class<? extends Module>) ClassUtils
                    .forName("com.fasterxml.jackson.datatype.jdk8.Jdk8Module", null);
            com.fasterxml.jackson.databind.Module jdk8Module = BeanUtils.instantiateClass(jdk8ModuleClass);
            modules.add(jdk8Module);
        } catch (ClassNotFoundException ex) {
            // jackson-datatype-jdk8 not available
        }

        try {
            Class<? extends com.fasterxml.jackson.databind.Module> javaTimeModuleClass = (Class<? extends Module>) ClassUtils
                    .forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule", null);
            com.fasterxml.jackson.databind.Module javaTimeModule = BeanUtils.instantiateClass(javaTimeModuleClass);
            modules.add(javaTimeModule);
        } catch (ClassNotFoundException ex) {
            // jackson-datatype-jsr310 not available
        }

        try {
            Class<? extends com.fasterxml.jackson.databind.Module> parameterNamesModuleClass = (Class<? extends Module>) ClassUtils
                    .forName("com.fasterxml.jackson.module.paramnames.ParameterNamesModule", null);
            com.fasterxml.jackson.databind.Module parameterNamesModule = BeanUtils
                    .instantiateClass(parameterNamesModuleClass);
            modules.add(parameterNamesModule);
        } catch (ClassNotFoundException ex) {
            // jackson-module-parameter-names not available
        }

        // Kotlin present?
        if (KotlinDetector.isKotlinPresent()) {
            try {
                Class<? extends com.fasterxml.jackson.databind.Module> kotlinModuleClass = (Class<? extends Module>) ClassUtils
                        .forName("com.fasterxml.jackson.module.kotlin.KotlinModule", null);
                Module kotlinModule = BeanUtils.instantiateClass(kotlinModuleClass);
                modules.add(kotlinModule);
            } catch (ClassNotFoundException ex) {
                // jackson-module-kotlin not available
            }
        }
        return modules;
    }

    /**
     * 提供响应的预期格式，指示它应遵循生成的JSON模式。
     *
     * @return 指令格式字符串。
     */
    @Override
    public String getFormat() {
        return String.format(getFormatTemplate(), this.jsonSchema);
    }

    /**
     * Provides the template for the format instruction. Subclasses can override this
     * method to customize the instruction format.
     *
     * @return The format template string.
     */
    protected String getFormatTemplate() {
        String lineSeparator = System.lineSeparator();
        return "Your response should be in JSON format." + lineSeparator
                + "Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation." + lineSeparator
                + "Do not include markdown code blocks in your response." + lineSeparator
                + "Remove the ```json markdown from the output." + lineSeparator
                + "Here is the JSON Schema instance your output must adhere to:" + lineSeparator
                + "```%s```";
    }


    /**
     * 为目标类型提供生成的JSON模式。
     *
     * @return 生成的JSON模式。
     */
    public String getJsonSchema() {
        return this.jsonSchema;
    }

    public Map<String, Object> getJsonSchemaMap() {
        try {
            return this.objectMapper.readValue(this.jsonSchema, Map.class);
        } catch (JsonProcessingException ex) {
            logger.error("Could not parse the JSON Schema to a Map object", ex);
            throw new IllegalStateException(ex);
        }
    }

}
