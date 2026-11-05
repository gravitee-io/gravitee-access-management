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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An element of an authorization_details array, immutable down to its nested values. Members without a getter are read
 * as map entries.
 * Expression Language reads every member as a map entry, so an absent common member is {@code null} there.
 * <p>
 * See <a href="https://datatracker.ietf.org/doc/html/rfc9396#section-2">RFC 9396 Section 2</a>
 */
public final class AuthorizationDetail extends AbstractMap<String, Object> {

    public static final String TYPE = "type";
    public static final String LOCATIONS = "locations";
    public static final String ACTIONS = "actions";
    public static final String DATATYPES = "datatypes";
    public static final String IDENTIFIER = "identifier";
    public static final String PRIVILEGES = "privileges";

    private final Map<String, Object> members;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public AuthorizationDetail(Map<String, ?> members) {
        this.members = copyOf(members);
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return members.entrySet();
    }

    @Override
    public Object get(Object key) {
        return members.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return members.containsKey(key);
    }

    @Override
    public int size() {
        return members.size();
    }

    public String getType() {
        return string(TYPE);
    }

    public List<String> getLocations() {
        return strings(LOCATIONS);
    }

    public List<String> getActions() {
        return strings(ACTIONS);
    }

    public List<String> getDatatypes() {
        return strings(DATATYPES);
    }

    public String getIdentifier() {
        return string(IDENTIFIER);
    }

    public List<String> getPrivileges() {
        return strings(PRIVILEGES);
    }

    private String string(String member) {
        return get(member) instanceof String value ? value : null;
    }

    /**
     * Skips entries that are not strings.
     */
    private List<String> strings(String member) {
        return get(member) instanceof List<?> values
                ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
    }

    private static Map<String, Object> copyOf(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put((String) key, copyOfValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyOfValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copyOf(map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(copyOfValue(element)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
