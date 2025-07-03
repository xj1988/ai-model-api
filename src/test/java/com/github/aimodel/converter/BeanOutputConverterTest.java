package com.github.aimodel.converter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author Sebastian Ullrich
 * @author Kirk Lund
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Konstantin Pavlov
 */
@ExtendWith(MockitoExtension.class)
class BeanOutputConverterTest {

    @Test
    void shouldHavePreConfiguredDefaultObjectMapper() {
        BeanOutputConverter<TestClass> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {

        });
        ObjectMapper objectMapper = converter.getObjectMapper();
        assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
    }

    static class TestClass {

        private String someString;

        @SuppressWarnings("unused")
        TestClass() {
        }

        TestClass(String someString) {
            this.someString = someString;
        }

        String getSomeString() {
            return this.someString;
        }

        public void setSomeString(String someString) {
            this.someString = someString;
        }

    }

    static class TestClassWithDateProperty {

        private LocalDate someString;

        @SuppressWarnings("unused")
        TestClassWithDateProperty() {
        }

        TestClassWithDateProperty(LocalDate someString) {
            this.someString = someString;
        }

        LocalDate getSomeString() {
            return this.someString;
        }

        public void setSomeString(LocalDate someString) {
            this.someString = someString;
        }

    }

    static class TestClassWithJsonAnnotations {

        @JsonProperty("string_property")
        @JsonPropertyDescription("string_property_description")
        private String someString;

        TestClassWithJsonAnnotations() {
        }

        String getSomeString() {
            return this.someString;
        }

    }

    @JsonPropertyOrder({"string_property", "foo_property", "bar_property"})
    static class TestClassWithJsonPropertyOrder {

        @JsonProperty("string_property")
        @JsonPropertyDescription("string_property_description")
        private final String someString;

        @JsonProperty(required = true, value = "bar_property")
        private final String bar;

        @JsonProperty(required = true, value = "foo_property")
        private final String foo;

        public TestClassWithJsonPropertyOrder(String someString, String bar, String foo) {
            this.someString = someString;
            this.bar = bar;
            this.foo = foo;
        }

        public String getSomeString() {
            return someString;
        }

        public String getBar() {
            return bar;
        }

        public String getFoo() {
            return foo;
        }
    }

    //class ConverterTest {

    @Test
    void convertClassType() {
        BeanOutputConverter<TestClass> converter = new BeanOutputConverter<>(TestClass.class);
        TestClass testClass = converter.convert("{ \"someString\": \"some value\" }");
        assertThat(testClass.getSomeString()).isEqualTo("some value");
    }

    @Test
    void convertClassWithDateType() {
        BeanOutputConverter<TestClassWithDateProperty> converter = new BeanOutputConverter<>(TestClassWithDateProperty.class);
        TestClassWithDateProperty testClass = converter.convert("{ \"someString\": \"2020-01-01\" }");
        assertThat(testClass.getSomeString()).isEqualTo(LocalDate.of(2020, 1, 1));
    }

    @Test
    void convertTypeReference() {
        BeanOutputConverter<TestClass> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {

        });
        TestClass testClass = converter.convert("{ \"someString\": \"some value\" }");
        assertThat(testClass.getSomeString()).isEqualTo("some value");
    }

    @Test
    void convertTypeReferenceArray() {
        BeanOutputConverter<List<TestClass>> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<TestClass>>() {

        });
        List<TestClass> testClass = converter.convert("[{ \"someString\": \"some value\" }]");
        assertThat(testClass).hasSize(1);
        assertThat(testClass.get(0).getSomeString()).isEqualTo("some value");
    }

    @Test
    void convertClassTypeWithJsonAnnotations() {
        BeanOutputConverter<TestClassWithJsonAnnotations> converter = new BeanOutputConverter<>(TestClassWithJsonAnnotations.class);
        TestClassWithJsonAnnotations testClass = converter.convert("{ \"string_property\": \"some value\" }");
        assertThat(testClass.getSomeString()).isEqualTo("some value");
    }

    @Test
    void verifySchemaPropertyOrder() throws Exception {
        BeanOutputConverter<TestClassWithJsonPropertyOrder> converter = new BeanOutputConverter<>(TestClassWithJsonPropertyOrder.class);
        String jsonSchema = converter.getJsonSchema();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode schemaNode = mapper.readTree(jsonSchema);

        List<String> actualOrder = new ArrayList<>();
        schemaNode.get("properties").fieldNames().forEachRemaining(actualOrder::add);

        assertThat(actualOrder).containsExactly("string_property", "foo_property", "bar_property");
    }

    @Test
    void convertTypeReferenceWithJsonAnnotations() {
        BeanOutputConverter<TestClassWithJsonAnnotations> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClassWithJsonAnnotations>() {

        });
        TestClassWithJsonAnnotations testClass = converter.convert("{ \"string_property\": \"some value\" }");
        assertThat(testClass.getSomeString()).isEqualTo("some value");
    }

    @Test
    void convertTypeReferenceArrayWithJsonAnnotations() {
        BeanOutputConverter<List<TestClassWithJsonAnnotations>> converter = new BeanOutputConverter<>(
                new ParameterizedTypeReference<List<TestClassWithJsonAnnotations>>() {

                });
        List<TestClassWithJsonAnnotations> testClass = converter
                .convert("[{ \"string_property\": \"some value\" }]");
        assertThat(testClass).hasSize(1);
        assertThat(testClass.get(0).getSomeString()).isEqualTo("some value");
    }

    //}

    // @checkstyle:off RegexpSinglelineJavaCheck
    //class FormatTest {

    @Test
    void formatClassType() {
        BeanOutputConverter<TestClass> converter = new BeanOutputConverter<>(TestClass.class);
        TextBlockAssertion.assertThat(converter.getFormat())
                .isEqualTo(
                        "Your response should be in JSON format.\n"
                                + "Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.\n"
                                + "Do not include markdown code blocks in your response.\n"
                                + "Remove the ```json markdown from the output.\n"
                                + "Here is the JSON Schema instance your output must adhere to:\n"
                                + "```{\n"
                                + "  \"$schema\" : \"https://json-schema.org/draft/2020-12/schema\",\n"
                                + "  \"type\" : \"object\",\n"
                                + "  \"properties\" : {\n"
                                + "    \"someString\" : {\n"
                                + "      \"type\" : \"string\"\n"
                                + "    }\n"
                                + "  },\n"
                                + "  \"additionalProperties\" : false\n"
                                + "}```");
    }

    @Test
    void formatTypeReference() {
        BeanOutputConverter<TestClass> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {

        });
        TextBlockAssertion.assertThat(converter.getFormat())
                .isEqualTo(
                        "Your response should be in JSON format.\n"
                                + "Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.\n"
                                + "Do not include markdown code blocks in your response.\n"
                                + "Remove the ```json markdown from the output.\n"
                                + "Here is the JSON Schema instance your output must adhere to:\n"
                                + "```{\n"
                                + "  \"$schema\" : \"https://json-schema.org/draft/2020-12/schema\",\n"
                                + "  \"type\" : \"object\",\n"
                                + "  \"properties\" : {\n"
                                + "    \"someString\" : {\n"
                                + "      \"type\" : \"string\"\n"
                                + "    }\n"
                                + "  },\n"
                                + "  \"additionalProperties\" : false\n"
                                + "}```");
    }

    @Test
    void formatTypeReferenceArray() {
        BeanOutputConverter<List<TestClass>> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<List<TestClass>>() {

        });
        TextBlockAssertion.assertThat(converter.getFormat())
                .isEqualTo(
                        "Your response should be in JSON format.\n"
                                + "Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.\n"
                                + "Do not include markdown code blocks in your response.\n"
                                + "Remove the ```json markdown from the output.\n"
                                + "Here is the JSON Schema instance your output must adhere to:\n"
                                + "```{\n"
                                + "  \"$schema\" : \"https://json-schema.org/draft/2020-12/schema\",\n"
                                + "  \"type\" : \"array\",\n"
                                + "  \"items\" : {\n"
                                + "    \"type\" : \"object\",\n"
                                + "    \"properties\" : {\n"
                                + "      \"someString\" : {\n"
                                + "        \"type\" : \"string\"\n"
                                + "      }\n"
                                + "    },\n"
                                + "    \"additionalProperties\" : false\n"
                                + "  }\n"
                                + "}```");
    }

    @Test
    void formatClassTypeWithAnnotations() {
        BeanOutputConverter<TestClassWithJsonAnnotations> converter = new BeanOutputConverter<>(TestClassWithJsonAnnotations.class);
        TextBlockAssertion.assertThat(converter.getFormat()).contains(
                "```{\n"
                        + "  \"$schema\" : \"https://json-schema.org/draft/2020-12/schema\",\n"
                        + "  \"type\" : \"object\",\n"
                        + "  \"properties\" : {\n"
                        + "    \"string_property\" : {\n"
                        + "      \"type\" : \"string\",\n"
                        + "      \"description\" : \"string_property_description\"\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"additionalProperties\" : false\n"
                        + "}```");
    }

    @Test
    void formatTypeReferenceWithAnnotations() {
        BeanOutputConverter<TestClassWithJsonAnnotations> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClassWithJsonAnnotations>() {

        });
        TextBlockAssertion.assertThat(converter.getFormat()).contains(
                "```{\n"
                        + "  \"$schema\" : \"https://json-schema.org/draft/2020-12/schema\",\n"
                        + "  \"type\" : \"object\",\n"
                        + "  \"properties\" : {\n"
                        + "    \"string_property\" : {\n"
                        + "      \"type\" : \"string\",\n"
                        + "      \"description\" : \"string_property_description\"\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"additionalProperties\" : false\n"
                        + "}```");
    }
    // @checkstyle:on RegexpSinglelineJavaCheck

    @Test
    void normalizesLineEndingsClassType() {
        BeanOutputConverter<TestClass> converter = new BeanOutputConverter<>(TestClass.class);

        String formatOutput = converter.getFormat();

        // validate that output contains \n line endings
        assertThat(formatOutput).contains(System.lineSeparator());
        //.doesNotContain("\r\n").doesNotContain("\r");
    }

    @Test
    void normalizesLineEndingsTypeReference() {
        BeanOutputConverter<TestClass> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<TestClass>() {

        });

        String formatOutput = converter.getFormat();

        // validate that output contains \n line endings
        assertThat(formatOutput).contains(System.lineSeparator());
        //.doesNotContain("\r\n").doesNotContain("\r");
    }

    //}

}