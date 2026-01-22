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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.URLParametersUtils;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Single;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;


@RequiredArgsConstructor
public enum ProviderResponseMode {
    QUERY("query") {
        @Override
        public MultiValueMap<String, String> extractParameters(Request request) {
            return new LinkedMultiValueMap<>(request.parameters());
        }
    },
    FRAGMENT("fragment") {
        @Override
        public MultiValueMap<String, String> extractParameters(Request request) {
            final String hashValue = request.parameters().getFirst(ConstantKeys.URL_HASH_PARAMETER);
            if (hashValue == null || hashValue.length() <= 1) { //missing param or we just got the '#'
                return new LinkedMultiValueMap<>();
            }
            Map<String, String> hashValues = URLParametersUtils.parse(hashValue.substring(1));

            var map = new LinkedMultiValueMap<String, String>();
            hashValues.forEach(map::add);
            return map;
        }
    },
    FORM_POST("form_post") {
        @Override
        public MultiValueMap<String, String> extractParameters(Request request) {
            return new LinkedMultiValueMap<>(request.parameters());

        }
    },
    DEFAULT("default") {
        @Override
        public MultiValueMap<String, String> extractParameters(Request request) {
            throw new IllegalStateException("can't extract parameters");
        }
    };

    @Getter
    private final String value;

    @JsonValue
    public String toJson() {
        return getValue();
    }

    @JsonCreator
    public static ProviderResponseMode fromJson(String value) {
        for (ProviderResponseMode mode : ProviderResponseMode.values()) {
            if (mode.getValue().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }

    public abstract MultiValueMap<String, String> extractParameters(Request request);

}
