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
package io.gravitee.am.gateway.handler.root.service.user.model;

import io.gravitee.am.model.account.ForgotPasswordLookupField;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ForgotPasswordParameters {

    private final Map<String, String> lookupValues;
    private final boolean customFormEnabled;
    private final boolean confirmIdentityEnabled;

    public ForgotPasswordParameters(String email,
                                    String username,
                                    boolean customFormEnabled,
                                    boolean confirmIdentityEnabled) {
        this(buildLookupValues(email, username), customFormEnabled, confirmIdentityEnabled);
    }

    public ForgotPasswordParameters(String email,
                                    boolean customFormEnabled,
                                    boolean confirmIdentityEnabled) {
        this(email, null, customFormEnabled, confirmIdentityEnabled);
    }

    public ForgotPasswordParameters(Map<String, String> lookupValues,
                                    boolean customFormEnabled,
                                    boolean confirmIdentityEnabled) {
        this.lookupValues = lookupValues == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(lookupValues));
        this.customFormEnabled = customFormEnabled;
        this.confirmIdentityEnabled = confirmIdentityEnabled;
    }

    public String getEmail() {
        return lookupValues.get(ForgotPasswordLookupField.EMAIL);
    }

    public String getUsername() {
        return lookupValues.get(ForgotPasswordLookupField.USERNAME);
    }

    public Map<String, String> getLookupValues() {
        return lookupValues;
    }

    public boolean hasAnyLookupValue() {
        return lookupValues.values().stream().anyMatch(value -> !StringUtils.isEmpty(value));
    }

    public boolean canFallbackToIdentityProvider() {
        return !StringUtils.isEmpty(getEmail()) || !StringUtils.isEmpty(getUsername());
    }

    public FilterCriteria buildCriteria() {
        final ArrayList<FilterCriteria> filterComponents = new ArrayList<>();
        lookupValues.forEach((key, value) -> {
            if (!StringUtils.isEmpty(value)) {
                filterComponents.add(buildCriteria(ForgotPasswordLookupField.toFilterFieldName(key), value));
            }
        });

        if (filterComponents.isEmpty()) {
            return new FilterCriteria();
        }
        if (filterComponents.size() == 1) {
            return filterComponents.get(0);
        }

        FilterCriteria criteria = new FilterCriteria();
        criteria.setOperator("and");
        criteria.setFilterComponents(filterComponents);
        return criteria;
    }

    private static Map<String, String> buildLookupValues(String email, String username) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!StringUtils.isEmpty(email)) {
            values.put(ForgotPasswordLookupField.EMAIL, email);
        }
        if (!StringUtils.isEmpty(username)) {
            values.put(ForgotPasswordLookupField.USERNAME, username);
        }
        return values;
    }

    private FilterCriteria buildCriteria(String scimField, String value) {
        final FilterCriteria criteria = new FilterCriteria();
        criteria.setFilterName(scimField);
        criteria.setFilterValue(value);
        criteria.setOperator("eq");
        criteria.setQuoteFilterValue(true);
        return criteria;
    }

    public boolean isCustomFormEnabled() {
        return customFormEnabled;
    }

    public boolean isConfirmIdentityEnabled() {
        return confirmIdentityEnabled;
    }
}
