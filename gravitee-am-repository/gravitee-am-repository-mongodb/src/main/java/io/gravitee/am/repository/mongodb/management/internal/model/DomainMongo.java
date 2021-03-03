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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.repository.mongodb.common.model.Auditable;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.OIDCSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.uma.UMASettingsMongo;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainMongo extends Auditable {

    @BsonId
    private String id;

    private String name;

    private String description;

    private boolean enabled;

    private String path;

    private boolean vhostMode;

    private List<VirtualHost> vhosts;

    private OIDCSettingsMongo oidc;

    private UMASettingsMongo uma;

    private SCIMSettingsMongo scim;

    private LoginSettingsMongo loginSettings;

    private WebAuthnSettingsMongo webAuthnSettings;

    private AccountSettingsMongo accountSettings;

    private PasswordSettingsMongo passwordSettings;

    private Set<String> tags;

    private ReferenceType referenceType;

    private String referenceId;

    private Set<String> identities;

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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public OIDCSettingsMongo getOidc() {
        return oidc;
    }

    public void setOidc(OIDCSettingsMongo oidc) {
        this.oidc = oidc;
    }

    public UMASettingsMongo getUma() {
        return uma;
    }

    public void setUma(UMASettingsMongo uma) {
        this.uma = uma;
    }

    public SCIMSettingsMongo getScim() {
        return scim;
    }

    public void setScim(SCIMSettingsMongo scim) {
        this.scim = scim;
    }

    public LoginSettingsMongo getLoginSettings() {
        return loginSettings;
    }

    public void setLoginSettings(LoginSettingsMongo loginSettings) {
        this.loginSettings = loginSettings;
    }

    public WebAuthnSettingsMongo getWebAuthnSettings() {
        return webAuthnSettings;
    }

    public void setWebAuthnSettings(WebAuthnSettingsMongo webAuthnSettings) {
        this.webAuthnSettings = webAuthnSettings;
    }

    public AccountSettingsMongo getAccountSettings() {
        return accountSettings;
    }

    public void setAccountSettings(AccountSettingsMongo accountSettings) {
        this.accountSettings = accountSettings;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
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

        DomainMongo that = (DomainMongo) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
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

    public boolean isVhostMode() {
        return vhostMode;
    }

    public void setVhostMode(boolean vhostMode) {
        this.vhostMode = vhostMode;
    }

    public List<VirtualHost> getVhosts() {
        return vhosts;
    }

    public void setVhosts(List<VirtualHost> vhosts) {
        this.vhosts = vhosts;
    }

    public PasswordSettingsMongo getPasswordSettings() {
        return passwordSettings;
    }

    public void setPasswordSettings(PasswordSettingsMongo passwordSettings) {
        this.passwordSettings = passwordSettings;
    }
}
