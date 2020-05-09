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
package io.gravitee.am.model;

import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;

import java.util.Date;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Domain implements Resource {

    /**
     * Domain identifier.
     */
    private String id;

    /**
     * Domain name.
     */
    private String name;

    /**
     * Domain description.
     */
    private String description;

    /**
     * Domain enabled.
     */
    private boolean enabled;

    /**
     * Domain creation date
     */
    private Date createdAt;

    /**
     * Domain last updated date
     */
    private Date updatedAt;

    /**
     * Domain HTTP path
     */
    private String path;

    private OIDCSettings oidc;

    private UMASettings uma;

    private LoginSettings loginSettings;

    private SCIMSettings scim;

    private AccountSettings accountSettings;

    private Set<String> tags;

    /**
     * The type of reference the domain is attached to (for now, should be ENVIRONMENT).
     */
    private ReferenceType referenceType;

    /**
     * The id of the reference the domain is attached to (for now, should be the environment id).
     */
    private String referenceId;

    /**
     * NOTE: only use for the DefaultOrganizationUpgrader
     */
    private Set<String> identities;

    public Domain() {
    }

    public Domain(Domain other) {
        this.id = other.id;
        this.name = other.name;
        this.description = other.description;
        this.enabled = other.enabled;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
        this.path = other.path;
        this.oidc = other.oidc;
        this.uma = other.uma;
        this.loginSettings = other.loginSettings;
        this.scim = other.scim;
        this.accountSettings = other.accountSettings;
        this.tags = other.tags;
        this.referenceType = other.referenceType;
        this.referenceId = other.referenceId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public OIDCSettings getOidc() {
        return oidc;
    }

    public void setOidc(OIDCSettings oidc) {
        this.oidc = oidc;
    }

    public UMASettings getUma() {
        return uma;
    }

    public void setUma(UMASettings uma) {
        this.uma = uma;
    }

    public SCIMSettings getScim() {
        return scim;
    }

    public void setScim(SCIMSettings scim) {
        this.scim = scim;
    }

    public LoginSettings getLoginSettings() {
        return loginSettings;
    }

    public void setLoginSettings(LoginSettings loginSettings) {
        this.loginSettings = loginSettings;
    }

    public AccountSettings getAccountSettings() {
        return accountSettings;
    }

    public void setAccountSettings(AccountSettings accountSettings) {
        this.accountSettings = accountSettings;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public boolean isDynamicClientRegistrationEnabled() {
        return this.getOidc()!=null &&
                this.getOidc().getClientRegistrationSettings()!=null &&
                this.getOidc().getClientRegistrationSettings().isDynamicClientRegistrationEnabled();
    }

    public boolean isOpenDynamicClientRegistrationEnabled() {
        return this.getOidc()!=null &&
                this.getOidc().getClientRegistrationSettings()!=null &&
                this.getOidc().getClientRegistrationSettings().isDynamicClientRegistrationEnabled() &&
                this.getOidc().getClientRegistrationSettings().isOpenDynamicClientRegistrationEnabled();
    }

    public boolean isDynamicClientRegistrationTemplateEnabled() {
        return this.getOidc()!=null &&
                this.getOidc().getClientRegistrationSettings()!=null &&
                this.getOidc().getClientRegistrationSettings().isClientTemplateEnabled();
    }

    public boolean isRedirectUriLocalhostAllowed() {
        return this.getOidc()!=null &&
                this.getOidc().getClientRegistrationSettings()!=null &&
                this.getOidc().getClientRegistrationSettings().isAllowLocalhostRedirectUri();
    }

    public boolean isRedirectUriUnsecuredHttpSchemeAllowed() {
        return this.getOidc()!=null &&
                this.getOidc().getClientRegistrationSettings()!=null &&
                this.getOidc().getClientRegistrationSettings().isAllowHttpSchemeRedirectUri();
    }

    public boolean isRedirectUriWildcardAllowed() {
        return this.getOidc()!=null &&
                this.getOidc().getClientRegistrationSettings()!=null &&
                this.getOidc().getClientRegistrationSettings().isAllowWildCardRedirectUri();
    }

    public boolean isRedirectUriStrictMatching() {
        return this.getOidc()!=null && this.getOidc().isRedirectUriStrictMatching();
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public Set<String> getIdentities() {
        return identities;
    }

    public void setIdentities(Set<String> identities) {
        this.identities = identities;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Domain domain = (Domain) o;

        return id.equals(domain.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
