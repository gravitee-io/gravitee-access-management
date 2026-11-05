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
package io.gravitee.am.model.oauth2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * The authorization_details of a request, read-only. Serializes as the JSON array it was built from.
 * <p>
 * See <a href="https://datatracker.ietf.org/doc/html/rfc9396#section-2">RFC 9396 Section 2</a>
 */
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class AuthorizationDetails {

    @EqualsAndHashCode.Include
    private final List<AuthorizationDetail> all;

    private final Map<String, List<AuthorizationDetail>> byType;

    private AuthorizationDetails(List<AuthorizationDetail> all) {
        this.all = all;
        this.byType = Collections.unmodifiableMap(all.stream()
                .filter(element -> element.getType() != null)
                .collect(groupingBy(AuthorizationDetail::getType, LinkedHashMap::new,
                        collectingAndThen(toList(), Collections::unmodifiableList))));
    }

    @JsonCreator
    public static AuthorizationDetails of(List<? extends Map<String, ?>> elements) {
        return new AuthorizationDetails(elements.stream()
                .map(element -> element instanceof AuthorizationDetail detail ? detail : new AuthorizationDetail(element))
                .toList());
    }

    /**
     * In request order.
     */
    public List<AuthorizationDetail> getAll() {
        return all;
    }

    /**
     * Keys in the order each type first appears.
     */
    public Map<String, List<AuthorizationDetail>> getByType() {
        return byType;
    }

    public Set<String> getTypes() {
        return byType.keySet();
    }

    /**
     * The elements as received: in order and unchanged.
     */
    @JsonValue
    public List<Map<String, Object>> getRaw() {
        return Collections.unmodifiableList(all);
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }
}
