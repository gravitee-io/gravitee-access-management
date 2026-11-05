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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An element of an authorization_details array. Members without a getter are read as map entries.
 * Expression Language reads every member as a map entry, so an absent common member is {@code null} there.
 * <p>
 * See <a href="https://datatracker.ietf.org/doc/html/rfc9396#section-2">RFC 9396 Section 2</a>
 */
public class AuthorizationDetail extends LinkedHashMap<String, Object> {

    public static final String TYPE = "type";
    public static final String LOCATIONS = "locations";
    public static final String ACTIONS = "actions";
    public static final String DATATYPES = "datatypes";
    public static final String IDENTIFIER = "identifier";
    public static final String PRIVILEGES = "privileges";

    public AuthorizationDetail() {
    }

    public AuthorizationDetail(Map<String, ?> members) {
        super(members);
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
}
