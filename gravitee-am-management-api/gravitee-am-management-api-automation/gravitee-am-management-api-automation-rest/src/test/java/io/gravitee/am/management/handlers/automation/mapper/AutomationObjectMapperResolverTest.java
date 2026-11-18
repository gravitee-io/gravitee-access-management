/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.management.handlers.automation.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationObjectMapperResolverTest {

    private final ObjectMapper mapper = new AutomationObjectMapperResolver().getContext(ObjectMapper.class);

    @Test
    void shouldOmitEmptyCollectionsAndKeepEmptyStrings() {
        JsonNode json = mapper.valueToTree(new Sample());

        assertThat(json.has("emptyList")).isFalse();
        assertThat(json.has("emptyMap")).isFalse();
        assertThat(json.has("nullString")).isFalse();
        assertThat(json.get("emptyString").asText()).isEmpty();
        assertThat(json.get("flag").asBoolean()).isFalse();
        assertThat(json.get("list").size()).isEqualTo(1);
        assertThat(json.get("map").get("key").asText()).isEmpty();
    }

    private static class Sample {
        private final List<String> emptyList = List.of();
        private final Map<String, String> emptyMap = Map.of();
        private final String nullString = null;
        private final String emptyString = "";
        private final boolean flag = false;
        private final List<String> list = List.of("value");
        private final Map<String, String> map = Map.of("key", "");
    }
}
