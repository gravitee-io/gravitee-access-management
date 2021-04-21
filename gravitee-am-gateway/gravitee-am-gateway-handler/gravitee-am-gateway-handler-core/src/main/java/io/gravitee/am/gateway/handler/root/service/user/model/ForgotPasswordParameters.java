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

import io.gravitee.am.repository.management.api.search.FilterCriteria;
import org.springframework.util.StringUtils;

import java.util.ArrayList;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ForgotPasswordParameters {
    final String email;
    final String username;
    final boolean customFormEnabled;
    final boolean confirmIdentityEnabled;

    public ForgotPasswordParameters(String email,
                                    String username,
                                    boolean customFormEnabled,
                                    boolean confirmIdentityEnabled) {
        this.email = email;
        this.username = username;
        this.customFormEnabled = customFormEnabled;
        this.confirmIdentityEnabled = confirmIdentityEnabled;
    }

    public ForgotPasswordParameters(String email,
                                    boolean customFormEnabled,
                                    boolean confirmIdentityEnabled) {
        this.email = email;
        this.username = null;
        this.customFormEnabled = customFormEnabled;
        this.confirmIdentityEnabled = confirmIdentityEnabled;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }


    public FilterCriteria buildCriteria() {
        final ArrayList<FilterCriteria> filterComponents = new ArrayList<>();
        if (!StringUtils.isEmpty(email)) {
            filterComponents.add(buildCriteria("emails.value", email));
        }
        if (!StringUtils.isEmpty(username)) {
            filterComponents.add(buildCriteria("userName", username));
        }

        FilterCriteria criteria = new FilterCriteria();
        if (filterComponents.size() == 1) {
            criteria = filterComponents.get(0);
        } else {
            criteria.setOperator("and");
            criteria.setFilterComponents(filterComponents);
        }

        return criteria;
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
