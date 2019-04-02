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

import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.login.LoginForm;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.scim.SCIMSettings;

import java.util.Date;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Domain {

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
     * Domain master flag.
     */
    private boolean master;

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

    private LoginForm loginForm;

    private Set<String> identities;

    private Set<String> oauth2Identities;

    private Event lastEvent;

    private OIDCSettings oidc;

    private LoginSettings loginSettings;

    private SCIMSettings scim;

    public Domain() {
    }

    public Domain(Domain other) {
        this.id = other.id;
        this.name = other.name;
        this.description = other.description;
        this.enabled = other.enabled;
        this.master = other.master;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
        this.path = other.path;
        this.loginForm = other.loginForm;
        this.identities = other.identities;
        this.oauth2Identities = other.oauth2Identities;
        this.lastEvent = other.lastEvent;
        this.oidc = other.oidc;
        this.loginSettings = other.loginSettings;
        this.scim = other.scim;
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

    public boolean isMaster() {
        return master;
    }

    public void setMaster(boolean master) {
        this.master = master;
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

    public LoginForm getLoginForm() {
        return loginForm;
    }

    public void setLoginForm(LoginForm loginForm) {
        this.loginForm = loginForm;
    }

    public Set<String> getIdentities() {
        return identities;
    }

    public void setIdentities(Set<String> identities) {
        this.identities = identities;
    }

    public Set<String> getOauth2Identities() {
        return oauth2Identities;
    }

    public void setOauth2Identities(Set<String> oauth2Identities) {
        this.oauth2Identities = oauth2Identities;
    }

    public Event getLastEvent() {
        return lastEvent;
    }

    public void setLastEvent(Event lastEvent) {
        this.lastEvent = lastEvent;
    }

    public OIDCSettings getOidc() {
        return oidc;
    }

    public void setOidc(OIDCSettings oidc) {
        this.oidc = oidc;
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
