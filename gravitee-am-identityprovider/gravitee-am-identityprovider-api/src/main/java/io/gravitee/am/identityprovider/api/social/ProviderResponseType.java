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
package io.gravitee.am.identityprovider.api.social;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

public record ProviderResponseType(String value, ProviderResponseMode defaultResponseMode) {
    public static final ProviderResponseType CODE = new ProviderResponseType("code", ProviderResponseMode.QUERY);
    public static final ProviderResponseType ID_TOKEN = new ProviderResponseType("id_token", ProviderResponseMode.FRAGMENT);
    public static final ProviderResponseType ID_TOKEN_TOKEN = new ProviderResponseType("id_token token", ProviderResponseMode.FRAGMENT);

    public static final List<ProviderResponseType> KNOWN_TYPES = List.of(CODE, ID_TOKEN, ID_TOKEN_TOKEN);

    @JsonValue
    public String toJson() {
        return value();
    }

    @JsonCreator
    public static ProviderResponseType fromJson(String value) {
        return KNOWN_TYPES.stream()
                .filter(x -> x.value().equals(value))
                .findFirst()
                .orElseGet(() -> new ProviderResponseType(value, ProviderResponseMode.QUERY));
    }
}
