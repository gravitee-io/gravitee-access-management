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
package io.gravitee.am.gateway.handler.oidc.discovery.impl;

import io.gravitee.am.common.oauth2.CodeChallengeMethod;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.common.oidc.ClaimType;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDProviderMetadata;
import io.gravitee.am.model.Domain;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
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

    @Value("${oidc.iss:http://gravitee.am}")
    private String iss;

    @Autowired
    private Domain domain;

    @Override
    public OpenIDProviderMetadata getConfiguration(String basePath) {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();

        // issuer
        openIDProviderMetadata.setIssuer(iss);

        // endpoints
        openIDProviderMetadata.setAuthorizationEndpoint(getEndpointAbsoluteURL(basePath, AUTHORIZATION_ENDPOINT));
        openIDProviderMetadata.setTokenEndpoint(getEndpointAbsoluteURL(basePath, TOKEN_ENDPOINT));
        openIDProviderMetadata.setUserinfoEndpoint(getEndpointAbsoluteURL(basePath, USERINFO_ENDPOINT));
        openIDProviderMetadata.setJwksUri(getEndpointAbsoluteURL(basePath, JWKS_URI));
        openIDProviderMetadata.setRevocationEndpoint(getEndpointAbsoluteURL(basePath, REVOCATION_ENDPOINT));
        openIDProviderMetadata.setIntrospectionEndpoint(getEndpointAbsoluteURL(basePath, INTROSPECTION_ENDPOINT));
        openIDProviderMetadata.setEndSessionEndpoint(getEndpointAbsoluteURL(basePath, ENDSESSION_ENDPOINT));

        // supported parameters
        openIDProviderMetadata.setScopesSupported(Stream.of(Scope.values()).map(Scope::getName).collect(Collectors.toList()));
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE, ResponseType.TOKEN, io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN, io.gravitee.am.common.oidc.ResponseType.CODE_TOKEN, io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN_TOKEN));
        openIDProviderMetadata.setGrantTypesSupported(Arrays.asList(GrantType.CLIENT_CREDENTIALS, GrantType.PASSWORD, GrantType.IMPLICIT, GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN, GrantType.JWT_BEARER));
        openIDProviderMetadata.setIdTokenSigningAlgValuesSupported(Arrays.asList(SignatureAlgorithm.RS256.getValue(), SignatureAlgorithm.RS512.getValue(), SignatureAlgorithm.HS512.getValue()));
        openIDProviderMetadata.setTokenEndpointAuthMethodsSupported(Arrays.asList(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, ClientAuthenticationMethod.CLIENT_SECRET_POST));
        openIDProviderMetadata.setClaimTypesSupported(Arrays.asList(ClaimType.NORMAL));
        openIDProviderMetadata.setClaimsSupported(Stream.of(Scope.values()).map(Scope::getClaims).flatMap(Collection::stream).distinct().collect(Collectors.toList()));
        openIDProviderMetadata.setCodeChallengeMethodsSupported(Arrays.asList(CodeChallengeMethod.PLAIN, CodeChallengeMethod.S256));
        openIDProviderMetadata.setClaimsParameterSupported(true);

        return openIDProviderMetadata;
    }

    private String getEndpointAbsoluteURL(String basePath, String endpointPath) {
        return basePath + domain.getPath() + endpointPath;
    }
}
