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

package com.github.aimodel.chat.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.github.aimodel.chat.messages.Media;
import com.github.aimodel.chat.messages.Message;
import com.github.aimodel.chat.messages.UserMessage;
import com.github.aimodel.template.StTemplateRenderer;
import com.github.aimodel.template.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StreamUtils;

/**
 * 用于创建提示的模板。它允许您为变量定义一个带有占位符的模板字符串，然后为这些变量呈现具有特定值的模板。
 */
public class PromptTemplate implements PromptTemplateActions, PromptTemplateMessageActions {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplate.class);

    private static final TemplateRenderer DEFAULT_TEMPLATE_RENDERER = StTemplateRenderer.builder().build();

    /**
     * 如果您要对此类进行子类化，请重新考虑将内置实现与新的PromptTemplateRenderer接口一起使用，该接口旨在为您提供更多灵活性和对渲染过程的控制。
     */
    private String template;

    private final Map<String, Object> variables = new HashMap<>();

    private final TemplateRenderer renderer;

    public PromptTemplate(Resource resource) {
        this(resource, new HashMap<>(), DEFAULT_TEMPLATE_RENDERER);
    }

    public PromptTemplate(String template) {
        this(template, new HashMap<>(), DEFAULT_TEMPLATE_RENDERER);
    }

    PromptTemplate(String template, Map<String, Object> variables, TemplateRenderer renderer) {
        Assert.hasText(template, "template cannot be null or empty");
        Assert.notNull(variables, "variables cannot be null");
        Assert.noNullElements(variables.keySet(), "variables keys cannot be null");
        Assert.notNull(renderer, "renderer cannot be null");

        this.template = template;
        this.variables.putAll(variables);
        this.renderer = renderer;
    }

    PromptTemplate(Resource resource, Map<String, Object> variables, TemplateRenderer renderer) {
        Assert.notNull(resource, "resource cannot be null");
        Assert.notNull(variables, "variables cannot be null");
        Assert.noNullElements(variables.keySet(), "variables keys cannot be null");
        Assert.notNull(renderer, "renderer cannot be null");

        try (InputStream inputStream = resource.getInputStream()) {
            this.template = StreamUtils.copyToString(inputStream, Charset.defaultCharset());
            Assert.hasText(this.template, "template cannot be null or empty");
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read resource", ex);
        }
        this.variables.putAll(variables);
        this.renderer = renderer;
    }

    public void add(String name, Object value) {
        this.variables.put(name, value);
    }

    public String getTemplate() {
        return this.template;
    }

    @Override
    public String render() {
        // 在渲染之前处理内部变量以处理资源
        Map<String, Object> processedVariables = new HashMap<>();
        for (Entry<String, Object> entry : this.variables.entrySet()) {
            if (entry.getValue() instanceof Resource) {
                processedVariables.put(entry.getKey(), renderResource((Resource) entry.getValue()));
            } else {
                processedVariables.put(entry.getKey(), entry.getValue());
            }
        }
        return this.renderer.apply(this.template, processedVariables);
    }

    @Override
    public String render(Map<String, Object> additionalVariables) {
        Map<String, Object> combinedVariables = new HashMap<>(this.variables);

        for (Entry<String, Object> entry : additionalVariables.entrySet()) {
            if (entry.getValue() instanceof Resource) {
                combinedVariables.put(entry.getKey(), renderResource((Resource) entry.getValue()));
            } else {
                combinedVariables.put(entry.getKey(), entry.getValue());
            }
        }

        return this.renderer.apply(this.template, combinedVariables);
    }

    private String renderResource(Resource resource) {
        if (resource == null) {
            return "";
        }

        try {
            // Handle ByteArrayResource specially
            if (resource instanceof ByteArrayResource) {
                return new String(((ByteArrayResource) resource).getByteArray(), StandardCharsets.UTF_8);
            }
            // If the resource exists but is empty
            if (!resource.exists() || resource.contentLength() == 0) {
                return "";
            }
            // For other Resource types or as fallback
            return getContentAsString(resource, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to render resource: {}", resource.getDescription(), e);
            return "[Unable to render resource: " + resource.getDescription() + "]";
        }
    }

    public static String getContentAsString(Resource resource, Charset charset) throws IOException {
        return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream(), charset));
    }

    // From PromptTemplateMessageActions.

    @Override
    public Message createMessage() {
        return new UserMessage(render());
    }

    @Override
    public Message createMessage(List<Media> mediaList) {
        return UserMessage.builder().text(render()).media(mediaList).build();
    }

    @Override
    public Message createMessage(Map<String, Object> additionalVariables) {
        return new UserMessage(render(additionalVariables));
    }

    // From PromptTemplateActions.

    @Override
    public Prompt create() {
        return new Prompt(render(new HashMap<>()));
    }

    @Override
    public Prompt create(ChatOptions modelOptions) {
        return Prompt.builder().content(render(new HashMap<>())).chatOptions(modelOptions).build();
    }

    @Override
    public Prompt create(Map<String, Object> additionalVariables) {
        return new Prompt(render(additionalVariables));
    }

    @Override
    public Prompt create(Map<String, Object> additionalVariables, ChatOptions modelOptions) {
        return Prompt.builder().content(render(additionalVariables)).chatOptions(modelOptions).build();
    }

    public Builder mutate() {
        return new Builder().template(this.template).variables(this.variables).renderer(this.renderer);
    }

    // Builder

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String template;

        private Resource resource;

        private Map<String, Object> variables = new HashMap<>();

        private TemplateRenderer renderer = DEFAULT_TEMPLATE_RENDERER;

        private Builder() {
        }

        public Builder template(String template) {
            Assert.hasText(template, "template cannot be null or empty");
            this.template = template;
            return this;
        }

        public Builder resource(Resource resource) {
            Assert.notNull(resource, "resource cannot be null");
            this.resource = resource;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            Assert.notNull(variables, "variables cannot be null");
            Assert.noNullElements(variables.keySet(), "variables keys cannot be null");
            this.variables = variables;
            return this;
        }

        public Builder renderer(TemplateRenderer renderer) {
            Assert.notNull(renderer, "renderer cannot be null");
            this.renderer = renderer;
            return this;
        }

        public PromptTemplate build() {
            if (this.template != null && this.resource != null) {
                throw new IllegalArgumentException("Only one of template or resource can be set");
            } else if (this.resource != null) {
                return new PromptTemplate(this.resource, this.variables, this.renderer);
            } else {
                return new PromptTemplate(this.template, this.variables, this.renderer);
            }
        }

    }

}