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
package io.gravitee.am.gateway.handler.oidc.service.clientregistration.impl;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.ClientService;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.CookieSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationAdvancedSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.utils.GrantTypeUtils;
import io.gravitee.risk.assessment.api.assessment.Assessment;
import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
public class ClientServiceImpl implements ClientService {

    public static final String DEFAULT_CLIENT_NAME = "Unknown Client";
    private final Logger LOGGER = LoggerFactory.getLogger(ClientServiceImpl.class);

    @Autowired
    private ApplicationService applicationService;

    @Override
    public Maybe<Client> findById(String id) {
        LOGGER.debug("Find client by ID: {}", id);
        return applicationService.findById(id)
                .map(application -> {
                    Client client = application.toClient();
                    // Send an empty array in case of no grant types
                    if (client.getAuthorizedGrantTypes() == null) {
                        client.setAuthorizedGrantTypes(Collections.emptyList());
                    }
                    return client;
                });
    }

    @Override
    public Single<Client> create(Domain domain, Client client) {
        LOGGER.debug("Create a client {} for domain {}", client, client.getDomain());

        if (client.getDomain() == null || client.getDomain().trim().isEmpty()) {
            return Single.error(new InvalidClientMetadataException("No domain set on client"));
        }

        boolean clientIdGenerated = false;
        client.setId(client.getId() != null ? client.getId() : RandomString.generate());
        // client_id & client_secret may be already informed if created through UI
        if (client.getClientId() == null) {
            client.setClientId(SecureRandomString.generate());
            clientIdGenerated = true;
        }
        if (client.getClientSecret() == null || client.getClientSecret().trim().isEmpty()) {
            client.setClientSecret(SecureRandomString.generate());
        }
        if (client.getClientName() == null || client.getClientName().trim().isEmpty()) {

            if (clientIdGenerated) {
                client.setClientName(DEFAULT_CLIENT_NAME);
            } else {
                // ClientId has been provided by user, reuse it as clientName.
                client.setClientName(client.getClientId());
            }
        }

        /* GRAVITEE.IO custom fields */
        client.setEnabled(true);

        client.setCreatedAt(new Date());
        client.setUpdatedAt(client.getCreatedAt());

        return applicationService.create(domain, convert(client)).map(Application::toClient);
    }

    @Override
    public Single<Client> update(Client client) {
        LOGGER.debug("Update client {} for domain {}", client.getClientId(), client.getDomain());

        if (client.getDomain() == null || client.getDomain().trim().isEmpty()) {
            return Single.error(new InvalidClientMetadataException("No domain set on client"));
        }

        return applicationService.update(convert(client))
                .map(Application::toClient);
    }

    @Override
    public Completable delete(String clientId, User principal, Domain domain) {
        LOGGER.debug("Delete client {}", clientId);
        return applicationService.delete(clientId, principal, domain);
    }

    @Override
    public Single<Client> renewClientSecret(Domain domain, String id, User principal) {
        LOGGER.debug("Renew client secret for client {} in domain {}", id, domain);
        return applicationService.renewClientSecret(domain, id, principal)
                .map(Application::toClient);
    }

    private Application convert(Client client) {
        Application application = new Application();
        application.setId(client.getId());
        application.setDomain(client.getDomain());
        application.setEnabled(client.isEnabled());
        application.setTemplate(client.isTemplate());
        application.setCertificate(client.getCertificate());
        application.setIdentityProviders(client.getIdentityProviders());
        application.setMetadata(client.getMetadata());
        application.setCreatedAt(client.getCreatedAt());
        application.setUpdatedAt(client.getUpdatedAt());
        // set application name
        application.setName(client.getClientName());
        // set application type
        application.setType(getType(client));
        // set application settings
        application.setSettings(getSettings(client));
        // preserve client secret hash
        application.setSecrets(client.getClientSecrets());
        application.setSecretSettings(client.getSecretSettings());

        if (client.getFactors() != null) {
            application.setFactors(new HashSet<>(client.getFactors()));
        }

        return application;
    }

    private ApplicationType getType(Client client) {
        GrantTypeUtils.completeGrantTypeCorrespondance(client);

        // if client has no grant => SERVICE
        // if client has only client_credentials grant_type => SERVICE
        // if client has only implicit => BROWSER
        // else if client type is native => NATIVE
        // else => WEB
        if (client.getAuthorizedGrantTypes() == null || client.getAuthorizedGrantTypes().isEmpty()) {
            return ApplicationType.SERVICE;
        }
        if (client.getAuthorizedGrantTypes().size() == 1) {
            if (client.getAuthorizedGrantTypes().contains(GrantType.CLIENT_CREDENTIALS)) {
                return ApplicationType.SERVICE;
            }
            if (client.getAuthorizedGrantTypes().contains(GrantType.IMPLICIT)) {
                return ApplicationType.BROWSER;
            }
        }
        // if not application type, set WEB app type
        if (client.getApplicationType() == null) {
            return ApplicationType.WEB;
        }
        // check application type
        return switch (client.getApplicationType()) {
            case io.gravitee.am.common.oidc.ApplicationType.WEB -> ApplicationType.WEB;
            case io.gravitee.am.common.oidc.ApplicationType.NATIVE -> ApplicationType.NATIVE;
            case io.gravitee.am.common.oidc.ApplicationType.BROWSER -> ApplicationType.BROWSER;
            default -> ApplicationType.SERVICE;
        };
    }

    private ApplicationSettings getSettings(Client client) {
        // OAuth 2.0/OIDC settings
        ApplicationOAuthSettings oAuthSettings = new ApplicationOAuthSettings();
        oAuthSettings.setClientId(client.getClientId());
        oAuthSettings.setClientSecret(client.getClientSecret());
        oAuthSettings.setRedirectUris(client.getRedirectUris());
        oAuthSettings.setGrantTypes(client.getAuthorizedGrantTypes());
        oAuthSettings.setResponseTypes(client.getResponseTypes());
        oAuthSettings.setApplicationType(client.getApplicationType());
        oAuthSettings.setContacts(client.getContacts());
        oAuthSettings.setClientName(client.getClientName());
        oAuthSettings.setPolicyUri(client.getPolicyUri());
        oAuthSettings.setClientUri(client.getClientUri());
        oAuthSettings.setPolicyUri(client.getPolicyUri());
        oAuthSettings.setTosUri(client.getTosUri());
        oAuthSettings.setJwksUri(client.getJwksUri());
        oAuthSettings.setJwks(client.getJwks());
        oAuthSettings.setSectorIdentifierUri(client.getSectorIdentifierUri());
        oAuthSettings.setSubjectType(client.getSubjectType());
        oAuthSettings.setIdTokenSignedResponseAlg(client.getIdTokenSignedResponseAlg());
        oAuthSettings.setIdTokenEncryptedResponseAlg(client.getIdTokenEncryptedResponseAlg());
        oAuthSettings.setIdTokenEncryptedResponseEnc(client.getIdTokenEncryptedResponseEnc());
        oAuthSettings.setUserinfoSignedResponseAlg(client.getUserinfoSignedResponseAlg());
        oAuthSettings.setUserinfoEncryptedResponseAlg(client.getUserinfoEncryptedResponseAlg());
        oAuthSettings.setUserinfoEncryptedResponseEnc(client.getUserinfoEncryptedResponseEnc());
        oAuthSettings.setRequestObjectSigningAlg(client.getRequestObjectSigningAlg());
        oAuthSettings.setRequestObjectEncryptionAlg(client.getRequestObjectEncryptionAlg());
        oAuthSettings.setRequestObjectEncryptionEnc(client.getRequestObjectEncryptionEnc());
        oAuthSettings.setTokenEndpointAuthMethod(client.getTokenEndpointAuthMethod());
        oAuthSettings.setTokenEndpointAuthSigningAlg(client.getTokenEndpointAuthSigningAlg());
        oAuthSettings.setDefaultMaxAge(client.getDefaultMaxAge());
        oAuthSettings.setRequireAuthTime(client.getRequireAuthTime());
        oAuthSettings.setDefaultACRvalues(client.getDefaultACRvalues());
        oAuthSettings.setInitiateLoginUri(client.getInitiateLoginUri());
        oAuthSettings.setRequestUris(client.getRequestUris());
        oAuthSettings.setScopeSettings(client.getScopeSettings());
        oAuthSettings.setSoftwareId(client.getSoftwareId());
        oAuthSettings.setSoftwareVersion(client.getSoftwareVersion());
        oAuthSettings.setSoftwareStatement(client.getSoftwareStatement());
        oAuthSettings.setRegistrationAccessToken(client.getRegistrationAccessToken());
        oAuthSettings.setRegistrationClientUri(client.getRegistrationClientUri());
        oAuthSettings.setClientIdIssuedAt(client.getClientIdIssuedAt());
        oAuthSettings.setClientSecretExpiresAt(client.getClientSecretExpiresAt());
        oAuthSettings.setAccessTokenValiditySeconds(client.getAccessTokenValiditySeconds());
        oAuthSettings.setRefreshTokenValiditySeconds(client.getRefreshTokenValiditySeconds());
        oAuthSettings.setIdTokenValiditySeconds(client.getIdTokenValiditySeconds());
        oAuthSettings.setEnhanceScopesWithUserPermissions(client.isEnhanceScopesWithUserPermissions());
        oAuthSettings.setTokenCustomClaims(client.getTokenCustomClaims());
        oAuthSettings.setAuthorizationSignedResponseAlg(client.getAuthorizationSignedResponseAlg());
        oAuthSettings.setAuthorizationEncryptedResponseAlg(client.getAuthorizationEncryptedResponseAlg());
        oAuthSettings.setAuthorizationEncryptedResponseEnc(client.getAuthorizationEncryptedResponseEnc());
        oAuthSettings.setPostLogoutRedirectUris(client.getPostLogoutRedirectUris());
        // parameters to control client identity when application authenticate using mtls
        oAuthSettings.setTlsClientAuthSanDns(client.getTlsClientAuthSanDns());
        oAuthSettings.setTlsClientAuthSanEmail(client.getTlsClientAuthSanEmail());
        oAuthSettings.setTlsClientAuthSanIp(client.getTlsClientAuthSanIp());
        oAuthSettings.setTlsClientAuthSanUri(client.getTlsClientAuthSanUri());
        oAuthSettings.setTlsClientAuthSubjectDn(client.getTlsClientAuthSubjectDn());
        oAuthSettings.setTlsClientCertificateBoundAccessTokens((client.isTlsClientCertificateBoundAccessTokens()));
        oAuthSettings.setAccessTokenValiditySeconds(client.getAccessTokenValiditySeconds());
        oAuthSettings.setRequireParRequest(client.isRequireParRequest());
        // CIBA settings
        oAuthSettings.setBackchannelAuthRequestSignAlg(client.getBackchannelAuthRequestSignAlg());
        oAuthSettings.setBackchannelClientNotificationEndpoint(client.getBackchannelClientNotificationEndpoint());
        oAuthSettings.setBackchannelTokenDeliveryMode(client.getBackchannelTokenDeliveryMode());
        oAuthSettings.setBackchannelUserCodeParameter(client.getBackchannelUserCodeParameter());

        ApplicationSettings applicationSettings = new ApplicationSettings();
        // oauth settings
        applicationSettings.setOauth(oAuthSettings);

        // advanced settings
        ApplicationAdvancedSettings advancedSettings = new ApplicationAdvancedSettings();
        advancedSettings.setSkipConsent(client.getAutoApproveScopes() != null && client.getAutoApproveScopes().contains("true"));
        advancedSettings.setFlowsInherited(client.isFlowsInherited());
        applicationSettings.setAdvanced(advancedSettings);

        // account settings
        if (client.getAccountSettings() != null) {
            AccountSettings accountSettings = new AccountSettings(client.getAccountSettings());
            applicationSettings.setAccount(accountSettings);
        }

        if (client.getPasswordSettings() != null) {
            applicationSettings.setPasswordSettings(new PasswordSettings(client.getPasswordSettings()));
        }

        if (client.getLoginSettings() != null) {
            applicationSettings.setLogin(new LoginSettings(client.getLoginSettings()));
        }

        if (client.getCookieSettings() != null) {
            applicationSettings.setCookieSettings(new CookieSettings(client.getCookieSettings()));
        }

        if (client.getRiskAssessment() != null) {
            RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
            riskAssessment.setEnabled(client.getRiskAssessment().isEnabled());
            riskAssessment.setDeviceAssessment(cloneAssessmentSettings(client.getRiskAssessment().getDeviceAssessment()));
            riskAssessment.setGeoVelocityAssessment(cloneAssessmentSettings(client.getRiskAssessment().getGeoVelocityAssessment()));
            riskAssessment.setIpReputationAssessment(cloneAssessmentSettings(client.getRiskAssessment().getIpReputationAssessment()));
            applicationSettings.setRiskAssessment(riskAssessment);
        }

        if (client.getMfaSettings() != null) {
            applicationSettings.setMfa(new MFASettings(client.getMfaSettings()));
        }

        return applicationSettings;
    }

    private AssessmentSettings cloneAssessmentSettings(AssessmentSettings assessmentSettings) {
        AssessmentSettings result = new AssessmentSettings();
        result.setEnabled(assessmentSettings.isEnabled());
        HashMap<Assessment, Double> thresholds = new HashMap<>(assessmentSettings.getThresholds());
        result.setThresholds(thresholds);
        return result;
    }
}
