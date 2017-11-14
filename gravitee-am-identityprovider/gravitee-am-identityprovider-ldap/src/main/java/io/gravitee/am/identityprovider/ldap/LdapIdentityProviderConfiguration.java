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
package io.gravitee.am.identityprovider.ldap;

import io.gravitee.am.identityprovider.api.IdentityProviderConfiguration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LdapIdentityProviderConfiguration implements IdentityProviderConfiguration {

    private String contextSourceUrl;

    private String contextSourceBase;

    private String contextSourceUsername;

    private String contextSourcePassword;

    private String userSearchBase = "";

    private String userSearchFilter;

    private String groupSearchBase = "";

    private String groupSearchFilter = "(uniqueMember={0})";

    private String groupRoleAttribute = "cn";

    public String getContextSourceUrl() {
        return contextSourceUrl;
    }

    public void setContextSourceUrl(String contextSourceUrl) {
        this.contextSourceUrl = contextSourceUrl;
    }

    public String getContextSourceBase() {
        return contextSourceBase;
    }

    public void setContextSourceBase(String contextSourceBase) {
        this.contextSourceBase = contextSourceBase;
    }

    public String getContextSourceUsername() {
        return contextSourceUsername;
    }

    public void setContextSourceUsername(String contextSourceUsername) {
        this.contextSourceUsername = contextSourceUsername;
    }

    public String getContextSourcePassword() {
        return contextSourcePassword;
    }

    public void setContextSourcePassword(String contextSourcePassword) {
        this.contextSourcePassword = contextSourcePassword;
    }

    public String getUserSearchBase() {
        return userSearchBase;
    }

    public void setUserSearchBase(String userSearchBase) {
        this.userSearchBase = userSearchBase;
    }

    public String getUserSearchFilter() {
        return userSearchFilter;
    }

    public void setUserSearchFilter(String userSearchFilter) {
        this.userSearchFilter = userSearchFilter;
    }

    public String getGroupSearchBase() {
        return groupSearchBase;
    }

    public void setGroupSearchBase(String groupSearchBase) {
        this.groupSearchBase = groupSearchBase;
    }

    public String getGroupSearchFilter() {
        return groupSearchFilter;
    }

    public void setGroupSearchFilter(String groupSearchFilter) {
        this.groupSearchFilter = groupSearchFilter;
    }

    public String getGroupRoleAttribute() {
        return groupRoleAttribute;
    }

    public void setGroupRoleAttribute(String groupRoleAttribute) {
        this.groupRoleAttribute = groupRoleAttribute;
    }
}
