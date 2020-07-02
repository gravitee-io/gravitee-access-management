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
package io.gravitee.am.gateway.handler.oidc.service.discovery.impl;

import io.gravitee.am.common.oauth2.CodeChallengeMethod;
import io.gravitee.am.common.oauth2.ResponseMode;
import io.gravitee.am.common.oidc.AcrValues;
import io.gravitee.am.common.oidc.ClaimType;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.utils.GrantTypeUtils;
import io.gravitee.am.service.utils.ResponseTypeUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.common.oidc.ClientAuthenticationMethod.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class OpenIDDiscoveryServiceImpl implements OpenIDDiscoveryService {

    private static final String AUTHORIZATION_ENDPOINT = "/oauth/authorize";
    private static final String TOKEN_ENDPOINT = "/oauth/token";
    private static final String USERINFO_ENDPOINT = "/oidc/userinfo";
    private static final String JWKS_URI = "/oidc/.well-known/jwks.json";
    private static final String REVOCATION_ENDPOINT = "/oauth/revoke";
    private static final String INTROSPECTION_ENDPOINT = "/oauth/introspect";
    private static final String ENDSESSION_ENDPOINT = "/logout";
    private static final String REGISTRATION_ENDPOINT = "/oidc/register";
    private static final String OIDC_ENDPOINT = "/oidc";
    private static final String REQUEST_OBJECT_ENDPOINT = "/oidc/ros";

    @Autowired
    private Domain domain;

    @Autowired
    private ScopeService scopeService;

    @Override
    public OpenIDProviderMetadata getConfiguration(String basePath) {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();

        // issuer
        openIDProviderMetadata.setIssuer(getIssuer(basePath));

        // endpoints
        openIDProviderMetadata.setAuthorizationEndpoint(getEndpointAbsoluteURL(basePath, AUTHORIZATION_ENDPOINT));
        openIDProviderMetadata.setTokenEndpoint(getEndpointAbsoluteURL(basePath, TOKEN_ENDPOINT));
        openIDProviderMetadata.setUserinfoEndpoint(getEndpointAbsoluteURL(basePath, USERINFO_ENDPOINT));
        openIDProviderMetadata.setJwksUri(getEndpointAbsoluteURL(basePath, JWKS_URI));
        openIDProviderMetadata.setRevocationEndpoint(getEndpointAbsoluteURL(basePath, REVOCATION_ENDPOINT));
        openIDProviderMetadata.setIntrospectionEndpoint(getEndpointAbsoluteURL(basePath, INTROSPECTION_ENDPOINT));
        openIDProviderMetadata.setEndSessionEndpoint(getEndpointAbsoluteURL(basePath, ENDSESSION_ENDPOINT));
        openIDProviderMetadata.setRegistrationEndpoint(getEndpointAbsoluteURL(basePath, REGISTRATION_ENDPOINT));
        openIDProviderMetadata.setRegistrationRenewSecretEndpoint(openIDProviderMetadata.getRegistrationEndpoint()+"/:client_id/renew_secret");
        if(domain.isDynamicClientRegistrationTemplateEnabled()) {
            openIDProviderMetadata.setRegistrationTemplatesEndpoint(openIDProviderMetadata.getRegistrationEndpoint()+"_templates");
        }
        openIDProviderMetadata.setRequestObjectEndpoint(getEndpointAbsoluteURL(basePath, REQUEST_OBJECT_ENDPOINT));

        // supported parameters
        openIDProviderMetadata.setScopesSupported(scopeService.getDiscoveryScope());
        openIDProviderMetadata.setResponseTypesSupported(ResponseTypeUtils.getSupportedResponseTypes());
        openIDProviderMetadata.setResponseModesSupported(Arrays.asList(ResponseMode.QUERY, ResponseMode.FRAGMENT,
                io.gravitee.am.common.oidc.ResponseMode.QUERY_JWT, io.gravitee.am.common.oidc.ResponseMode.FRAGMENT_JWT,
                io.gravitee.am.common.oidc.ResponseMode.JWT));
        openIDProviderMetadata.setGrantTypesSupported(GrantTypeUtils.getSupportedGrantTypes());
        openIDProviderMetadata.setIdTokenSigningAlgValuesSupported(JWAlgorithmUtils.getSupportedIdTokenSigningAlg());
        openIDProviderMetadata.setIdTokenEncryptionAlgValuesSupported(JWAlgorithmUtils.getSupportedIdTokenResponseAlg());
        openIDProviderMetadata.setIdTokenEncryptionEncValuesSupported(JWAlgorithmUtils.getSupportedIdTokenResponseEnc());
        openIDProviderMetadata.setTokenEndpointAuthMethodsSupported(Arrays.asList(CLIENT_SECRET_BASIC, CLIENT_SECRET_POST, PRIVATE_KEY_JWT, CLIENT_SECRET_JWT, TLS_CLIENT_AUTH, SELF_SIGNED_TLS_CLIENT_AUTH));
        openIDProviderMetadata.setClaimTypesSupported(Collections.singletonList(ClaimType.NORMAL));
        openIDProviderMetadata.setClaimsSupported(Stream.of(StandardClaims.claims(), CustomClaims.claims(), Collections.singletonList(Claims.acr)).flatMap(c -> c.stream()).collect(Collectors.toList()));
        openIDProviderMetadata.setCodeChallengeMethodsSupported(Arrays.asList(CodeChallengeMethod.PLAIN, CodeChallengeMethod.S256));
        openIDProviderMetadata.setClaimsParameterSupported(true);
        openIDProviderMetadata.setUserinfoSigningAlgValuesSupported(JWAlgorithmUtils.getSupportedUserinfoSigningAlg());
        openIDProviderMetadata.setUserinfoEncryptionAlgValuesSupported(JWAlgorithmUtils.getSupportedUserinfoResponseAlg());
        openIDProviderMetadata.setUserinfoEncryptionEncValuesSupported(JWAlgorithmUtils.getSupportedUserinfoResponseEnc());
        openIDProviderMetadata.setAuthorizationSigningAlgValuesSupported(JWAlgorithmUtils.getSupportedAuthorizationSigningAlg());
        openIDProviderMetadata.setAuthorizationEncryptionAlgValuesSupported(JWAlgorithmUtils.getSupportedAuthorizationResponseAlg());
        openIDProviderMetadata.setAuthorizationEncryptionEncValuesSupported(JWAlgorithmUtils.getSupportedAuthorizationResponseEnc());
        openIDProviderMetadata.setAcrValuesSupported(AcrValues.values());
        return openIDProviderMetadata;
    }

    @Override
    public String getIssuer(String basePath) {
        return getEndpointAbsoluteURL(basePath, OIDC_ENDPOINT);
    }

    private String getEndpointAbsoluteURL(String basePath, String endpointPath) {
        return basePath + domain.getPath() + endpointPath;
    }
}
