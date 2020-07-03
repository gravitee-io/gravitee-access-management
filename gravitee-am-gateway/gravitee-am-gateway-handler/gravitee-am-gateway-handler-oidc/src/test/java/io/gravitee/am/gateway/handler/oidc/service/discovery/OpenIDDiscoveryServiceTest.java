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
package io.gravitee.am.gateway.handler.oidc.service.discovery;

import io.gravitee.am.common.oidc.AcrValues;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.impl.OpenIDDiscoveryServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils;
import io.gravitee.am.model.Domain;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertTrue;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class OpenIDDiscoveryServiceTest {

    @InjectMocks
    private OpenIDDiscoveryService openIDDiscoveryService = new OpenIDDiscoveryServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private ScopeService scopeService;

    @Test
    public void shouldContain_request_parameter_supported() {
        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(openIDProviderMetadata.getRequestParameterSupported());
    }

    public void shouldContain_id_token_signing_alg_values_supported() {
        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(JWAlgorithmUtils.getSupportedIdTokenSigningAlg().containsAll(openIDProviderMetadata.getIdTokenSigningAlgValuesSupported()));
    }

    @Test
    public void shouldContain_token_endpoint_auth_signing_alg_values_supported() {
        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(JWAlgorithmUtils.getSupportedTokenEndpointAuthSigningAlg().containsAll(openIDProviderMetadata.getTokenEndpointAuthSigningAlgValuesSupported()));
    }
    public void shouldContain_acr_claim_supported() {
        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(openIDProviderMetadata.getClaimsSupported().contains(Claims.acr));
    }

    @Test
    public void shouldContain_acr_values_supported() {
        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(openIDProviderMetadata.getAcrValuesSupported().containsAll(AcrValues.values()));
    }

    @Test
    public void shouldContain_userinfo_signing_alg_values_supported() {
        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(JWAlgorithmUtils.getSupportedUserinfoSigningAlg().containsAll(openIDProviderMetadata.getUserinfoSigningAlgValuesSupported()));
    }

    @Test
    public void shouldContain_request_object_signing_alg_values_supported() {
        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(JWAlgorithmUtils.getSupportedRequestObjectSigningAlg().containsAll(openIDProviderMetadata.getRequestObjectSigningAlgValuesSupported()));
    }
}

