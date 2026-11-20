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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.handlers.management.api.mapper.ObjectMapperResolver;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AutomationObjectMapperResolver implements ContextResolver<ObjectMapper> {

    private final ObjectMapper mapper = newAutomationObjectMapper();

    public static ObjectMapper newAutomationObjectMapper() {
        ObjectMapper mapper = ObjectMapperResolver.newManagementObjectMapper();
        mapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, JsonInclude.Include.USE_DEFAULTS));
        mapper.configOverride(String.class)
                .setInclude(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.USE_DEFAULTS));
        return mapper;
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return mapper;
    }
}
