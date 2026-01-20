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

import io.gravitee.am.common.utils.PathUtils;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Builder
@AllArgsConstructor
public class Domain implements Resource {

    /**
     * Domain identifier.
     */
    private String id;

    /**
     * Domain human readable identifier.
     */
    private String hrid;

    /**
     * Domain name.
     */
    private String name;
    /**
     * Domain Version when the entity has been created. (Since 4.5.0)
     * If this field is missing, it has to be considered as {@link DomainVersion#V1_0}
     */
    private DomainVersion version;

    /**
     * Domain description.
     */
    private String description;

    /**
     * The type of reference the domain is attached to (for now, should be ENVIRONMENT).
     */
    private ReferenceType referenceType;

    /**
     * The id of the reference the domain is attached to (for now, should be the environment id).
     */
    private String referenceId;

    /**
     * Domain enabled.
     */
    private boolean enabled;

    /**
     * Flag indicating if alerts are enabled or not.
     */
    private Boolean alertEnabled;

    /**
     * Domain HTTP path
     */
    private String path;

    /**
     * NOTE: only use for the DefaultOrganizationUpgrader
     */
    private Set<String> identities;

    /**
     * If domain is master, it can do cross domains token introspection
     */
    private boolean master;

    /**
     * Indicates if domain is in vhost mode or not. If true, then security domain will be exposed under specified virtual hosts.
     */
    private boolean vhostMode = false;

    /**
     * The list of vhosts to expose the domain on.
     */
    private List<VirtualHost> vhosts;

    /**
     * The list of sharding tags to expose the domain on.
     */
    private Set<String> tags;

    /**
     * Domain creation date
     */
    @Schema(type = "java.lang.Long")
    private Date createdAt;

    /**
     * Domain last updated date
     */
    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    /**
     * -------------------------
     * Advanced settings section
     * -------------------------
     */

    /**
     * OAuth 2.0 / OIDC settings
     */
    private OIDCSettings oidc;

    /**
     * UMA 2.0 settings
     */
    private UMASettings uma;

    /**
     * Login page settings
     */
    private LoginSettings loginSettings;

    /**
     * WebAuthn/Passwordless settings
     */
    private WebAuthnSettings webAuthnSettings;

    /**
     * SCIM 2.0 settings
     */
    private SCIMSettings scim;

    /**
     * User accounts settings
     */
    private AccountSettings accountSettings;

    /**
     * Password settings
     */
    private PasswordSettings passwordSettings;

    /**
     * Self-service Account Management settings
     */
    private SelfServiceAccountManagementSettings selfServiceAccountManagementSettings;

    /**
     * SAML 2.0 settings
     */
    private SAMLSettings saml;

    /**
     * CORS settings
     */
    private CorsSettings corsSettings;

    /**
     * ID of Data Plane
     */
    private String dataPlaneId;

    /**
     * Client secret settings
     */

    private SecretExpirationSettings secretExpirationSettings;

    /**
     * OAuth 2.0 Token Exchange settings (RFC 8693)
     */
    private TokenExchangeSettings tokenExchangeSettings;

    public Domain() {
    }

    /**
     * For testing purpose only
     * @param id
     */
    public Domain(String id) {
        this.id = id;
    }

    public Domain(Domain other) {
        this.id = other.id;
        this.hrid = other.hrid;
        this.name = other.name;
        this.version = other.version;
        this.description = other.description;
        this.referenceType = other.referenceType;
        this.referenceId = other.referenceId;
        this.enabled = other.enabled;
        this.alertEnabled = other.alertEnabled;
        this.path = other.path;
        this.identities = other.identities;
        this.master = other.master;
        this.vhostMode = other.vhostMode;
        this.vhosts = other.vhosts;
        this.tags = other.tags;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
        this.oidc = other.oidc;
        this.uma = other.uma;
        this.loginSettings = other.loginSettings;
        this.webAuthnSettings = other.webAuthnSettings;
        this.scim = other.scim;
        this.accountSettings = other.accountSettings;
        this.passwordSettings = other.passwordSettings;
        this.selfServiceAccountManagementSettings = other.selfServiceAccountManagementSettings;
        this.saml = other.saml;
        this.corsSettings = other.corsSettings;
        this.dataPlaneId = other.dataPlaneId;
        this.secretExpirationSettings = other.secretExpirationSettings;
        this.tokenExchangeSettings = other.tokenExchangeSettings;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHrid() {
        return hrid;
    }

    public void setHrid(String hrid) {
        this.hrid = hrid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DomainVersion getVersion() {
        return version == null ? DomainVersion.V1_0 : version;
    }

    public void setVersion(DomainVersion version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean isAlertEnabled() {
        return alertEnabled;
    }

    public void setAlertEnabled(Boolean alertEnabled) {
        this.alertEnabled = alertEnabled;
    }

    public String getPath() {
        return PathUtils.sanitize(path);
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Set<String> getIdentities() {
        return identities;
    }

    public void setIdentities(Set<String> identities) {
        this.identities = identities;
    }

    public boolean isMaster() {
        return master;
    }

    public void setMaster(boolean master) {
        this.master = master;
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

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
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

    public LoginSettings getLoginSettings() {
        return loginSettings;
    }

    public void setLoginSettings(LoginSettings loginSettings) {
        this.loginSettings = loginSettings;
    }

    public WebAuthnSettings getWebAuthnSettings() {
        return webAuthnSettings;
    }

    public void setWebAuthnSettings(WebAuthnSettings webAuthnSettings) {
        this.webAuthnSettings = webAuthnSettings;
    }

    public SCIMSettings getScim() {
        return scim;
    }

    public void setScim(SCIMSettings scim) {
        this.scim = scim;
    }

    public AccountSettings getAccountSettings() {
        return accountSettings;
    }

    public void setAccountSettings(AccountSettings accountSettings) {
        this.accountSettings = accountSettings;
    }

    public PasswordSettings getPasswordSettings() {
        return passwordSettings;
    }

    public void setPasswordSettings(PasswordSettings passwordSettings) {
        this.passwordSettings = passwordSettings;
    }

    public SelfServiceAccountManagementSettings getSelfServiceAccountManagementSettings() {
        return selfServiceAccountManagementSettings;
    }

    public void setSelfServiceAccountManagementSettings(SelfServiceAccountManagementSettings selfServiceAccountManagementSettings) {
        this.selfServiceAccountManagementSettings = selfServiceAccountManagementSettings;
    }

    public SAMLSettings getSaml() {
        return saml;
    }

    public void setSaml(SAMLSettings saml) {
        this.saml = saml;
    }

    public CorsSettings getCorsSettings() {
        return corsSettings;
    }

    public void setCorsSettings(CorsSettings corsSettings) {
        this.corsSettings = corsSettings;
    }

    public String getDataPlaneId() {
        return dataPlaneId;
    }

    public void setDataPlaneId(String dataPlaneId) {
        this.dataPlaneId = dataPlaneId;
    }

    public SecretExpirationSettings getSecretExpirationSettings() {
        return secretExpirationSettings;
    }

    public void setSecretExpirationSettings(SecretExpirationSettings secretExpirationSettings) {
        this.secretExpirationSettings = secretExpirationSettings;
    }

    public TokenExchangeSettings getTokenExchangeSettings() {
        return tokenExchangeSettings;
    }

    public void setTokenExchangeSettings(TokenExchangeSettings tokenExchangeSettings) {
        this.tokenExchangeSettings = tokenExchangeSettings;
    }

    public boolean isDynamicClientRegistrationEnabled() {
        return this.getOidc() != null &&
                this.getOidc().getClientRegistrationSettings() != null &&
                this.getOidc().getClientRegistrationSettings().isDynamicClientRegistrationEnabled();
    }

    public boolean useTokenExchange() {
        return this.getTokenExchangeSettings() != null &&
                this.getTokenExchangeSettings().isEnabled();
    }

    public boolean isOpenDynamicClientRegistrationEnabled() {
        return this.getOidc() != null &&
                this.getOidc().getClientRegistrationSettings() != null &&
                this.getOidc().getClientRegistrationSettings().isDynamicClientRegistrationEnabled() &&
                this.getOidc().getClientRegistrationSettings().isOpenDynamicClientRegistrationEnabled();
    }

    public boolean isDynamicClientRegistrationTemplateEnabled() {
        return this.getOidc() != null &&
                this.getOidc().getClientRegistrationSettings() != null &&
                this.getOidc().getClientRegistrationSettings().isClientTemplateEnabled();
    }

    public boolean isRedirectUriLocalhostAllowed() {
        return this.getOidc() != null &&
                this.getOidc().getClientRegistrationSettings() != null &&
                this.getOidc().getClientRegistrationSettings().isAllowLocalhostRedirectUri();
    }

    public boolean isRedirectUriUnsecuredHttpSchemeAllowed() {
        return this.getOidc() != null &&
                this.getOidc().getClientRegistrationSettings() != null &&
                this.getOidc().getClientRegistrationSettings().isAllowHttpSchemeRedirectUri();
    }

    public boolean isRedirectUriWildcardAllowed() {
        return this.getOidc() != null &&
                this.getOidc().getClientRegistrationSettings() != null &&
                this.getOidc().getClientRegistrationSettings().isAllowWildCardRedirectUri();
    }

    public boolean isRedirectUriStrictMatching() {
        return this.getOidc() != null && this.getOidc().isRedirectUriStrictMatching();
    }

    public boolean usePlainFapiProfile() {
        return this.getOidc() != null &&
                this.getOidc().getSecurityProfileSettings() != null &&
                this.getOidc().getSecurityProfileSettings().isEnablePlainFapi();
    }

    public boolean useFapiBrazilProfile() {
        return this.getOidc() != null &&
                this.getOidc().getSecurityProfileSettings() != null &&
                this.getOidc().getSecurityProfileSettings().isEnableFapiBrazil();
    }

    public boolean useCiba() {
        return this.getOidc() != null &&
                this.getOidc().getCibaSettings() != null &&
                this.getOidc().getCibaSettings().isEnabled();
    }

    public boolean isRedirectUriExpressionLanguageEnabled() {
        return this.getOidc() != null &&
                this.getOidc().getClientRegistrationSettings() != null &&
                this.getOidc().getClientRegistrationSettings().isAllowRedirectUriParamsExpressionLanguage();
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

    public final Reference asReference() {
        return Reference.domain(this.getId());
    }
}
