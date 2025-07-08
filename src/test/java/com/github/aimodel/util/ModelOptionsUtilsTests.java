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

package com.github.aimodel.util;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.aimodel.model.ModelOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 */
public class ModelOptionsUtilsTests {

    @Test
    public void merge() {
        TestPortableOptionsImpl portableOptions = new TestPortableOptionsImpl();
        portableOptions.setName("John");
        portableOptions.setAge(30);
        portableOptions.setNonInterfaceField("NonInterfaceField");

        TestSpecificOptions specificOptions = new TestSpecificOptions();
        specificOptions.setName("Mike");
        specificOptions.setSpecificField("SpecificField");

        assertThatThrownBy(
                () -> ModelOptionsUtils.merge(portableOptions, specificOptions, TestPortableOptionsImpl.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No @JsonProperty fields found in the ");

        TestSpecificOptions specificOptions2 = ModelOptionsUtils.merge(portableOptions, specificOptions, TestSpecificOptions.class);

        assertThat(specificOptions2.getAge()).isEqualTo(30);
        assertThat(specificOptions2.getName()).isEqualTo("John"); // !!! Overridden by the
        // portableOptions
        assertThat(specificOptions2.getSpecificField()).isEqualTo("SpecificField");
    }

    @Test
    public void objectToMap() {
        TestPortableOptionsImpl portableOptions = new TestPortableOptionsImpl();
        portableOptions.setName("John");
        portableOptions.setAge(30);
        portableOptions.setNonInterfaceField("NonInterfaceField");

        Map<String, Object> map = ModelOptionsUtils.objectToMap(portableOptions);

        assertThat(map).containsEntry("name", "John");
        assertThat(map).containsEntry("age", 30);
        assertThat(map).containsEntry("nonInterfaceField", "NonInterfaceField");
    }

    @Test
    public void mapToClass() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John");
        data.put("age", 30);
        data.put("nonInterfaceField", "NonInterfaceField");

        TestPortableOptionsImpl portableOptions = ModelOptionsUtils.mapToClass(
                data,
                TestPortableOptionsImpl.class);

        assertThat(portableOptions.getName()).isEqualTo("John");
        assertThat(portableOptions.getAge()).isEqualTo(30);
        assertThat(portableOptions.getNonInterfaceField()).isEqualTo("NonInterfaceField");
    }

    @Test
    public void mergeBeans() {

        TestPortableOptionsImpl portableOptions = new TestPortableOptionsImpl();
        portableOptions.setName("John");
        portableOptions.setAge(30);
        portableOptions.setNonInterfaceField("NonInterfaceField");

        TestSpecificOptions specificOptions = new TestSpecificOptions();

        specificOptions.setName("Mike");
        specificOptions.setAge(60);
        specificOptions.setSpecificField("SpecificField");

        TestSpecificOptions specificOptions2 = ModelOptionsUtils.mergeBeans(portableOptions, specificOptions,
                TestPortableOptions.class, false);

        assertThat(specificOptions2.getAge()).isEqualTo(60);
        assertThat(specificOptions2.getName()).isEqualTo("Mike");
        assertThat(specificOptions2.getSpecificField()).isEqualTo("SpecificField");

        TestSpecificOptions specificOptionsWithOverride = ModelOptionsUtils.mergeBeans(portableOptions, specificOptions,
                TestPortableOptions.class, true);

        assertThat(specificOptionsWithOverride.getAge()).isEqualTo(30);
        assertThat(specificOptionsWithOverride.getName()).isEqualTo("John");
        assertThat(specificOptionsWithOverride.getSpecificField()).isEqualTo("SpecificField");
    }

    @Test
    public void copyToTarget() {
        TestPortableOptionsImpl portableOptions = new TestPortableOptionsImpl();
        portableOptions.setName("John");
        portableOptions.setAge(30);
        portableOptions.setNonInterfaceField("NonInterfaceField");

        TestSpecificOptions target = ModelOptionsUtils.copyToTarget(portableOptions, TestPortableOptions.class,
                TestSpecificOptions.class);

        assertThat(target.getAge()).isEqualTo(30);
        assertThat(target.getName()).isEqualTo("John");
        assertThat(target.getSpecificField()).isNull();
    }

    @Test
    public void getJsonPropertyValues() {
        assertThat(ModelOptionsUtils.getJsonPropertyValues(TestRecord.class)).hasSize(2);
        assertThat(ModelOptionsUtils.getJsonPropertyValues(TestRecord.class)).containsExactly("field1", "field2");
    }

    public interface TestPortableOptions extends ModelOptions {

        String getName();

        void setName(String name);

        Integer getAge();

        void setAge(Integer age);

    }

    public static class TestPortableOptionsImpl implements TestPortableOptions {

        private String name;

        private Integer age;

        // Non interface fields
        private String nonInterfaceField;

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public Integer getAge() {
            return this.age;
        }

        @Override
        public void setAge(Integer age) {
            this.age = age;
        }

        public String getNonInterfaceField() {
            return this.nonInterfaceField;
        }

        public void setNonInterfaceField(String nonInterfaceField) {
            this.nonInterfaceField = nonInterfaceField;
        }

    }

    public static class TestSpecificOptions implements TestPortableOptions {

        @JsonProperty("specificField")
        private String specificField;

        @JsonProperty("name")
        private String name;

        @JsonProperty("age")
        private Integer age;

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public Integer getAge() {
            return this.age;
        }

        @Override
        public void setAge(Integer age) {
            this.age = age;
        }

        public String getSpecificField() {
            return this.specificField;
        }

        public void setSpecificField(String modelSpecificField) {
            this.specificField = modelSpecificField;
        }

        @Override
        public String toString() {
            return "TestModelSpecificOptions{" + "specificField='" + this.specificField + '\'' + ", name='" + this.name
                    + '\'' + ", age=" + this.age + '}';
        }

    }


    public static class TestRecord {

        @JsonProperty("field1")
        private String field1;

        @JsonProperty("field2")
        private String field2;

        public String getField1() {
            return field1;
        }

        public void setField1(String field1) {
            this.field1 = field1;
        }

        public String getField2() {
            return field2;
        }

        public void setField2(String field2) {
            this.field2 = field2;
        }
    }

}