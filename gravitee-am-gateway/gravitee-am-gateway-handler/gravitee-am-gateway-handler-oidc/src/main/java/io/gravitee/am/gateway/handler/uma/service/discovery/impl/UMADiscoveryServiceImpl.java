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
package io.gravitee.am.gateway.handler.uma.service.discovery.impl;

import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils;
import io.gravitee.am.gateway.handler.uma.service.discovery.UMADiscoveryService;
import io.gravitee.am.gateway.handler.uma.service.discovery.UMAProviderMetadata;
import io.gravitee.am.model.Domain;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;

import static io.gravitee.am.common.oidc.ClientAuthenticationMethod.*;
import static io.gravitee.am.gateway.handler.uma.constants.UMAConstants.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class UMADiscoveryServiceImpl implements UMADiscoveryService {

    @Autowired
    private OpenIDDiscoveryService oidcDiscoveryService;

    @Autowired
    private Domain domain;

    @Override
    public UMAProviderMetadata getConfiguration(String basePath) {

        OpenIDProviderMetadata oidcMetadata = oidcDiscoveryService.getConfiguration(basePath);
        UMAProviderMetadata umaMetadata = new UMAProviderMetadata();

        //Set Oauth2 metadata values
        umaMetadata
                .setIssuer(oidcMetadata.getIssuer())
                .setAuthorizationEndpoint(oidcMetadata.getAuthorizationEndpoint())
                .setTokenEndpoint(oidcMetadata.getTokenEndpoint())
                .setJwksUri(oidcMetadata.getJwksUri())
                .setRegistrationEndpoint(oidcMetadata.getRegistrationEndpoint())
                .setScopesSupported(oidcMetadata.getScopesSupported())
                .setResponseTypesSupported(oidcMetadata.getResponseTypesSupported())
                .setResponseModesSupported(oidcMetadata.getResponseModesSupported())
                .setGrantTypesSupported(oidcMetadata.getGrantTypesSupported())
                .setTokenEndpointAuthMethodsSupported(oidcMetadata.getTokenEndpointAuthMethodsSupported())
                .setTokenEndpointAuthSigningAlgValuesSupported(oidcMetadata.getTokenEndpointAuthSigningAlgValuesSupported())
                .setServiceDocumentation(oidcMetadata.getServiceDocumentation())
                .setUiLocalesSupported(oidcMetadata.getUiLocalesSupported())
                .setOpPolicyUri(oidcMetadata.getOpPolicyUri())
                .setOpTosUri(oidcMetadata.getOpTosUri())
                .setRevocationEndpoint(oidcMetadata.getRevocationEndpoint())
                .setRevocationEndpointAuthMethodsSupported(oidcMetadata.getTokenEndpointAuthMethodsSupported())
                .setRevocationEndpointAuthSigningAlgValuesSupported(oidcMetadata.getTokenEndpointAuthSigningAlgValuesSupported())
                .setIntrospectionEndpoint(oidcMetadata.getIntrospectionEndpoint())
                //See io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientAuthHandler
                .setIntrospectionEndpointAuthMethodsSupported(Arrays.asList(CLIENT_SECRET_BASIC, CLIENT_SECRET_POST, CLIENT_SECRET_JWT, PRIVATE_KEY_JWT, TLS_CLIENT_AUTH, SELF_SIGNED_TLS_CLIENT_AUTH))
                .setIntrospectionEndpointAuthSigningAlgValuesSupported(JWAlgorithmUtils.getSupportedIntrospectionEndpointAuthSigningAlg())
                .setCodeChallengeMethodsSupported(oidcMetadata.getCodeChallengeMethodsSupported());

        //Set UMA2 metadata values
        umaMetadata
                //UMA2 GRANT section
                .setClaimsInteractionEndpoint(getEndpointAbsoluteURL(basePath, CLAIMS_INTERACTION_PATH))
                .setUmaProfilesSupported(Collections.emptyList())
                //UMA2 Protection API section
                .setPermissionEndpoint(getEndpointAbsoluteURL(basePath, PERMISSION_PATH))
                .setResourceRegistrationEndpoint(getEndpointAbsoluteURL(basePath, RESOURCE_REGISTRATION_PATH));

        return umaMetadata;
    }

    private String getEndpointAbsoluteURL(String basePath, String endpointPath) {
        return basePath + UMA_PATH + endpointPath;
    }
}
