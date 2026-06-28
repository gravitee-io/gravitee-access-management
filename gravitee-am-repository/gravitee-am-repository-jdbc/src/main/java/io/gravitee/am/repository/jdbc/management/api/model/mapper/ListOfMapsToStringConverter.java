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
package io.gravitee.am.repository.jdbc.management.api.model.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.dozermapper.core.DozerConverter;
import io.gravitee.am.repository.jdbc.provider.common.JSONMapper;

import java.util.List;
import java.util.Map;

/**
 * Dozer converter for {@code List<Map<String, Object>>} ↔ JSON String (clob).
 * Used to persist RFC 9396 authorization_details on JDBC-backed entities.
 */
public class ListOfMapsToStringConverter extends DozerConverter<List, String> {

    public ListOfMapsToStringConverter() {
        super(List.class, String.class);
    }

    @Override
    public String convertTo(List source, String target) {
        return JSONMapper.toJson(source);
    }

    @Override
    public List convertFrom(String source, List target) {
        return JSONMapper.toCollectionOfBean(source, new TypeReference<List<Map<String, Object>>>() { });
    }
}
