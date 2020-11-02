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

    private boolean useStartTLS;

    private String contextSourceBase;

    private String contextSourceUsername;

    private String contextSourcePassword;

    private String userSearchBase = "";

    private String userSearchFilter;

    private boolean fetchGroups = true;

    private String groupSearchBase = "";

    private String groupSearchFilter = "(uniqueMember={0})";

    private String groupRoleAttribute = "cn";

    private String passwordAlgorithm;

    private Long connectTimeout = 5000l;

    private Long responseTimeout = 5000l;

    private Integer minPoolSize = 5;

    private Integer maxPoolSize = 15;

    private Integer maxPoolRetries = 3;

    private String passwordEncoding;

    private boolean hashEncodedByThirdParty;

    public String getContextSourceUrl() {
        return contextSourceUrl;
    }

    public void setContextSourceUrl(String contextSourceUrl) {
        this.contextSourceUrl = contextSourceUrl;
    }

    public boolean isUseStartTLS() {
        return useStartTLS;
    }

    public void setUseStartTLS(boolean useStartTLS) {
        this.useStartTLS = useStartTLS;
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

    public boolean isFetchGroups() {
        return fetchGroups;
    }

    public void setFetchGroups(boolean fetchGroups) {
        this.fetchGroups = fetchGroups;
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

    public Long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Long getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Long responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public Integer getMinPoolSize() {
        return minPoolSize;
    }

    public void setMinPoolSize(Integer minPoolSize) {
        this.minPoolSize = minPoolSize;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public Integer getMaxPoolRetries() {
        return maxPoolRetries;
    }

    public void setMaxPoolRetries(Integer maxPoolRetries) {
        this.maxPoolRetries = maxPoolRetries;
    }

    public String getPasswordAlgorithm() {
        return passwordAlgorithm;
    }

    public void setPasswordAlgorithm(String passwordAlgorithm) {
        this.passwordAlgorithm = passwordAlgorithm;
    }

    public String getPasswordEncoding() {
        return passwordEncoding;
    }

    public void setPasswordEncoding(String passwordEncoding) {
        this.passwordEncoding = passwordEncoding;
    }

    public boolean isHashEncodedByThirdParty() {
        return hashEncodedByThirdParty;
    }

    public void setHashEncodedByThirdParty(boolean hashEncodedByThirdParty) {
        this.hashEncodedByThirdParty = hashEncodedByThirdParty;
    }
}
