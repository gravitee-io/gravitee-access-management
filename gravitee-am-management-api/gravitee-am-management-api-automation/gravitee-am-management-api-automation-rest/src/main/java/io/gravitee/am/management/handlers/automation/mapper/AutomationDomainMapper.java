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
package io.gravitee.am.management.handlers.automation.mapper;

import io.gravitee.am.management.handlers.automation.model.AutomationAccountSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationCIBASettings;
import io.gravitee.am.management.handlers.automation.model.AutomationCertificateSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationDomain;
import io.gravitee.am.management.handlers.automation.model.AutomationOidcSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationSamlSettings;
import io.gravitee.am.management.handlers.automation.resource.AutomationIds;
import io.gravitee.am.model.CertificateSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.SAMLSettings;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.CIBASettings;
import io.gravitee.am.model.oidc.OIDCSettings;

import java.util.List;

/**
 * Maps between the shared {@link Domain} model and the symmetric {@link AutomationDomain} projection.
 * <p>
 * Settings blocks that don't carry cross-resource references are reused by reference. Certificates,
 * identity providers and reporters are discrete resources with their own endpoints; the domain only
 * <i>references</i> some of them by key:
 * <ul>
 *   <li>{@code saml.certificate} and {@code certificateSettings.fallbackCertificate} — certificate keys</li>
 *   <li>{@code accountSettings.defaultIdentityProviderForRegistration} — an identity-provider key</li>
 * </ul>
 * Each reference is stored twice on the shared model: the internal-id field (read by the gateway and
 * management API) and a parallel {@code *Key} field (owned by the Automation API). The key is stored
 * verbatim so GET round-trips losslessly even when the referenced resource does not exist yet (eventual
 * consistency); the internal id is computed deterministically from the key so no lookup or existence
 * check is required at write time.
 *
 * @author GraviteeSource Team
 */
public final class AutomationDomainMapper {

    private AutomationDomainMapper() {
    }

    // --- model -> Automation (response) -------------------------------------

    public static AutomationDomain toAutomationDomain(Domain domain) {
        AutomationDomain out = new AutomationDomain();
        out.setAutomationKey(domain.getAutomationKey());
        out.setName(domain.getName());
        out.setDescription(domain.getDescription());
        out.setEnabled(domain.isEnabled());
        out.setPath(domain.getPath());
        out.setTags(domain.getTags());
        out.setVhosts(domain.getVhosts());
        out.setDataPlaneId(domain.getDataPlaneId());
        out.setCreatedAt(domain.getCreatedAt());
        out.setUpdatedAt(domain.getUpdatedAt());

        // unchanged settings: reuse the shared sub-models by reference
        out.setUma(domain.getUma());
        out.setLoginSettings(domain.getLoginSettings());
        out.setWebAuthnSettings(domain.getWebAuthnSettings());
        out.setScim(domain.getScim());
        out.setPasswordSettings(domain.getPasswordSettings());
        out.setSelfServiceAccountManagementSettings(domain.getSelfServiceAccountManagementSettings());
        out.setCorsSettings(domain.getCorsSettings());
        out.setSecretExpirationSettings(domain.getSecretExpirationSettings());
        out.setTokenExchangeSettings(domain.getTokenExchangeSettings());

        // Wrapped settings blocks (key-keyed references read from the parallel *Key fields)
        out.setOidc(toAutomationOidc(domain.getOidc()));
        out.setAccountSettings(toAutomationAccountSettings(domain.getAccountSettings()));

        if (domain.getSaml() != null) {
            AutomationSamlSettings saml = new AutomationSamlSettings();
            saml.setEnabled(domain.getSaml().isEnabled());
            saml.setEntityId(domain.getSaml().getEntityId());
            saml.setCertificate(domain.getSaml().getCertificateKey());
            out.setSaml(saml);
        }
        if (domain.getCertificateSettings() != null) {
            AutomationCertificateSettings cs = new AutomationCertificateSettings();
            cs.setFallbackCertificate(domain.getCertificateSettings().getFallbackCertificateKey());
            out.setCertificateSettings(cs);
        }
        return out;
    }

    private static AutomationOidcSettings toAutomationOidc(OIDCSettings oidc) {
        if (oidc == null) {
            return null;
        }
        AutomationOidcSettings out = new AutomationOidcSettings();
        out.setClientRegistrationSettings(oidc.getClientRegistrationSettings());
        out.setSecurityProfileSettings(oidc.getSecurityProfileSettings());
        out.setRedirectUriStrictMatching(oidc.isRedirectUriStrictMatching());
        out.setPostLogoutRedirectUris(oidc.getPostLogoutRedirectUris());
        out.setRequestUris(oidc.getRequestUris());
        // cimdSettings is intentionally not surfaced — see AutomationOidcSettings javadoc.
        out.setCibaSettings(toAutomationCiba(oidc.getCibaSettings()));
        return out;
    }

    private static AutomationCIBASettings toAutomationCiba(CIBASettings ciba) {
        if (ciba == null) {
            return null;
        }
        AutomationCIBASettings out = new AutomationCIBASettings();
        out.setEnabled(ciba.isEnabled());
        out.setAuthReqExpiry(ciba.getAuthReqExpiry());
        out.setTokenReqInterval(ciba.getTokenReqInterval());
        out.setBindingMessageLength(ciba.getBindingMessageLength());
        return out;
    }

    private static AutomationAccountSettings toAutomationAccountSettings(AccountSettings account) {
        if (account == null) {
            return null;
        }
        AutomationAccountSettings out = new AutomationAccountSettings();
        out.setInherited(account.isInherited());
        out.setLoginAttemptsDetectionEnabled(account.isLoginAttemptsDetectionEnabled());
        out.setMaxLoginAttempts(account.getMaxLoginAttempts());
        out.setLoginAttemptsResetTime(account.getLoginAttemptsResetTime());
        out.setAccountBlockedDuration(account.getAccountBlockedDuration());
        out.setSendRecoverAccountEmail(account.isSendRecoverAccountEmail());
        out.setSendVerifyRegistrationAccountEmail(account.isSendVerifyRegistrationAccountEmail());
        out.setCompleteRegistrationWhenResetPassword(account.isCompleteRegistrationWhenResetPassword());
        out.setAutoLoginAfterRegistration(account.isAutoLoginAfterRegistration());
        out.setRedirectUriAfterRegistration(account.getRedirectUriAfterRegistration());
        out.setDynamicUserRegistration(account.isDynamicUserRegistration());
        out.setDefaultIdentityProviderForRegistration(account.getDefaultIdentityProviderForRegistrationKey());
        out.setAutoLoginAfterResetPassword(account.isAutoLoginAfterResetPassword());
        out.setRedirectUriAfterResetPassword(account.getRedirectUriAfterResetPassword());
        out.setDeletePasswordlessDevicesAfterResetPassword(account.isDeletePasswordlessDevicesAfterResetPassword());
        out.setRememberMe(account.isRememberMe());
        out.setRememberMeDuration(account.getRememberMeDuration());
        out.setResetPasswordCustomForm(account.isResetPasswordCustomForm());
        out.setResetPasswordCustomFormFields(account.getResetPasswordCustomFormFields());
        out.setResetPasswordConfirmIdentity(account.isResetPasswordConfirmIdentity());
        out.setResetPasswordInvalidateTokens(account.isResetPasswordInvalidateTokens());
        out.setMfaChallengeAttemptsDetectionEnabled(account.isMfaChallengeAttemptsDetectionEnabled());
        out.setMfaChallengeMaxAttempts(account.getMfaChallengeMaxAttempts());
        out.setMfaChallengeAttemptsResetTime(account.getMfaChallengeAttemptsResetTime());
        out.setMfaChallengeSendVerifyAlertEmail(account.isMfaChallengeSendVerifyAlertEmail());
        return out;
    }

    // --- Automation (request) -> model -------------------------------------

    /**
     * Apply the writable fields of {@code in} onto {@code target} with <b>strict declarative</b>
     * semantics: a settings block omitted from the payload is reset to its domain-creation default —
     * {@code null} for every feature settings block (its state in a freshly created domain), and
     * {@link OIDCSettings#defaultSettings()} for {@code oidc} (which must never be null). Each key
     * reference is written to both the internal-id field (computed deterministically from the key, or
     * resolved against {@code idps} for an identity provider so a default — which uses the conventional
     * id — is honoured) and its parallel {@code *Key} field.
     */
    public static void applyTo(AutomationDomain in, Domain target, List<IdentityProvider> idps) {
        target.setName(in.getName());
        target.setDescription(in.getDescription());
        target.setEnabled(in.isEnabled());
        target.setPath(in.getPath());
        target.setTags(in.getTags());
        target.setVhosts(in.getVhosts());

        // oidc must never be null — reset to the same default the domain is created with
        target.setOidc(in.getOidc() != null
                ? toModelOidc(in.getOidc())
                : OIDCSettings.defaultSettings());
        // every other settings block: omitted -> reset to its created-domain default (null)
        target.setUma(in.getUma());
        target.setLoginSettings(in.getLoginSettings());
        target.setWebAuthnSettings(in.getWebAuthnSettings());
        target.setScim(in.getScim());
        target.setAccountSettings(toModelAccountSettings(in.getAccountSettings(), target.getId(), idps));
        target.setPasswordSettings(in.getPasswordSettings());
        target.setSelfServiceAccountManagementSettings(in.getSelfServiceAccountManagementSettings());
        target.setCorsSettings(in.getCorsSettings());
        target.setSecretExpirationSettings(in.getSecretExpirationSettings());
        target.setTokenExchangeSettings(in.getTokenExchangeSettings());

        if (in.getSaml() != null) {
            SAMLSettings saml = new SAMLSettings();
            saml.setEnabled(in.getSaml().isEnabled());
            saml.setEntityId(in.getSaml().getEntityId());
            saml.setCertificate(certificateKeyToId(target.getId(), in.getSaml().getCertificate()));
            saml.setCertificateKey(in.getSaml().getCertificate());
            target.setSaml(saml);
        } else {
            target.setSaml(null);
        }
        if (in.getCertificateSettings() != null) {
            CertificateSettings cs = new CertificateSettings();
            cs.setFallbackCertificate(certificateKeyToId(target.getId(), in.getCertificateSettings().getFallbackCertificate()));
            cs.setFallbackCertificateKey(in.getCertificateSettings().getFallbackCertificate());
            target.setCertificateSettings(cs);
        } else {
            target.setCertificateSettings(null);
        }
    }

    private static OIDCSettings toModelOidc(AutomationOidcSettings in) {
        OIDCSettings out = new OIDCSettings();
        out.setClientRegistrationSettings(in.getClientRegistrationSettings());
        out.setSecurityProfileSettings(in.getSecurityProfileSettings());
        out.setRedirectUriStrictMatching(in.isRedirectUriStrictMatching());
        out.setPostLogoutRedirectUris(in.getPostLogoutRedirectUris());
        out.setRequestUris(in.getRequestUris());
        out.setCibaSettings(toModelCiba(in.getCibaSettings()));
        return out;
    }

    private static CIBASettings toModelCiba(AutomationCIBASettings in) {
        if (in == null) {
            return null;
        }
        CIBASettings out = new CIBASettings();
        out.setEnabled(in.isEnabled());
        out.setAuthReqExpiry(in.getAuthReqExpiry());
        out.setTokenReqInterval(in.getTokenReqInterval());
        out.setBindingMessageLength(in.getBindingMessageLength());
        return out;
    }

    private static AccountSettings toModelAccountSettings(AutomationAccountSettings in, String domainId, List<IdentityProvider> idps) {
        if (in == null) {
            return null;
        }
        AccountSettings out = new AccountSettings();
        out.setInherited(in.isInherited());
        out.setLoginAttemptsDetectionEnabled(in.isLoginAttemptsDetectionEnabled());
        out.setMaxLoginAttempts(in.getMaxLoginAttempts());
        out.setLoginAttemptsResetTime(in.getLoginAttemptsResetTime());
        out.setAccountBlockedDuration(in.getAccountBlockedDuration());
        out.setSendRecoverAccountEmail(in.isSendRecoverAccountEmail());
        out.setSendVerifyRegistrationAccountEmail(in.isSendVerifyRegistrationAccountEmail());
        out.setCompleteRegistrationWhenResetPassword(in.isCompleteRegistrationWhenResetPassword());
        out.setAutoLoginAfterRegistration(in.isAutoLoginAfterRegistration());
        out.setRedirectUriAfterRegistration(in.getRedirectUriAfterRegistration());
        out.setDynamicUserRegistration(in.isDynamicUserRegistration());
        out.setDefaultIdentityProviderForRegistration(
                identityProviderKeyToId(domainId, in.getDefaultIdentityProviderForRegistration(), idps));
        out.setDefaultIdentityProviderForRegistrationKey(in.getDefaultIdentityProviderForRegistration());
        out.setAutoLoginAfterResetPassword(in.isAutoLoginAfterResetPassword());
        out.setRedirectUriAfterResetPassword(in.getRedirectUriAfterResetPassword());
        out.setDeletePasswordlessDevicesAfterResetPassword(in.isDeletePasswordlessDevicesAfterResetPassword());
        out.setRememberMe(in.isRememberMe());
        out.setRememberMeDuration(in.getRememberMeDuration());
        out.setResetPasswordCustomForm(in.isResetPasswordCustomForm());
        out.setResetPasswordCustomFormFields(in.getResetPasswordCustomFormFields());
        out.setResetPasswordConfirmIdentity(in.isResetPasswordConfirmIdentity());
        out.setResetPasswordInvalidateTokens(in.isResetPasswordInvalidateTokens());
        out.setMfaChallengeAttemptsDetectionEnabled(in.isMfaChallengeAttemptsDetectionEnabled());
        out.setMfaChallengeMaxAttempts(in.getMfaChallengeMaxAttempts());
        out.setMfaChallengeAttemptsResetTime(in.getMfaChallengeAttemptsResetTime());
        out.setMfaChallengeSendVerifyAlertEmail(in.isMfaChallengeSendVerifyAlertEmail());
        return out;
    }

    // --- key -> id helpers --------------------------------------------------

    /**
     * A certificate's internal id is always the deterministic key-based id (certificates have no
     * conventional id, even when default), so it can be computed without a lookup or existence check.
     */
    private static String certificateKeyToId(String domainId, String key) {
        return key == null ? null : AutomationIds.certificateId(domainId, key);
    }

    /**
     * Resolve an identity-provider key to its internal id. A default provider adopts the conventional
     * {@code default-idp-<domainId>} id, so we resolve against the domain's existing automation-managed
     * providers when possible; if the provider does not exist yet (eventual consistency) we fall back to
     * the deterministic key-based id.
     */
    private static String identityProviderKeyToId(String domainId, String key, List<IdentityProvider> idps) {
        if (key == null) {
            return null;
        }
        return idps.stream()
                .filter(idp -> idp.isManagedBy(ManagedBy.AUTOMATION_API))
                .filter(idp -> key.equals(idp.getAutomationKey()))
                .map(IdentityProvider::getId)
                .findFirst()
                .orElseGet(() -> AutomationIds.identityProviderId(domainId, key));
    }
}
