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
import io.gravitee.am.common.oidc.BrazilAcrValues;
import io.gravitee.am.common.oidc.CIBADeliveryMode;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.impl.OpenIDDiscoveryServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.utils.JWAlgorithmUtils;
import io.gravitee.am.model.Domain;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.List;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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

    @Mock
    private Environment environment;

    @Before
    public void prepare() {
        when(environment.getProperty("http.secured", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("http.ssl.clientAuth", String.class, "none")).thenReturn("none");
    }

    private void enableMtls() {
        Mockito.reset(environment);
        when(environment.getProperty("http.secured", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("http.ssl.clientAuth", String.class, "none")).thenReturn("required");
        when(environment.getProperty(ConstantKeys.HTTP_SSL_ALIASES_BASE_URL, String.class, "/")).thenReturn("/");
    }

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

    @Test
    public void shouldContain_acr_claim_supported() {
        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(openIDProviderMetadata.getClaimsSupported().contains(Claims.acr));
    }

    @Test
    public void shouldContain_brazil_claim_supported() {
        when(domain.useFapiBrazilProfile()).thenReturn(true);
        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(openIDProviderMetadata.getClaimsSupported().contains(Claims.acr));
        assertTrue(openIDProviderMetadata.getClaimsSupported().containsAll(OpenIDDiscoveryServiceImpl.BRAZIL_CLAIMS));
    }

    @Test
    public void shouldContain_acr_values_supported() {
        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(openIDProviderMetadata.getAcrValuesSupported().containsAll(AcrValues.values()));
    }

    @Test
    public void shouldContain_brazil_acr_values_supported() {
        when(domain.useFapiBrazilProfile()).thenReturn(true);
        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(openIDProviderMetadata.getAcrValuesSupported().containsAll(AcrValues.values()));
        assertTrue(openIDProviderMetadata.getAcrValuesSupported().containsAll(BrazilAcrValues.values()));
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

    @Test
    public void shouldContain_tls_client_certificate_bound_access_tokens() throws Exception {
        ((OpenIDDiscoveryServiceImpl)openIDDiscoveryService).setMtlsAliasEndpoints(Arrays.asList(
                ConstantKeys.HTTP_SSL_ALIASES_ENDPOINTS_TOKEN,
                ConstantKeys.HTTP_SSL_ALIASES_ENDPOINTS_AUTHORIZATION,
                ConstantKeys.HTTP_SSL_ALIASES_ENDPOINTS_END_SESSION,
                ConstantKeys.HTTP_SSL_ALIASES_ENDPOINTS_PAR,
                ConstantKeys.HTTP_SSL_ALIASES_ENDPOINTS_REGISTRATION,
                ConstantKeys.HTTP_SSL_ALIASES_ENDPOINTS_REVOCATION,
                ConstantKeys.HTTP_SSL_ALIASES_ENDPOINTS_USERINFO,
                ConstantKeys.HTTP_SSL_ALIASES_ENDPOINTS_INTROSPECTION));

        OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertFalse(openIDProviderMetadata.getTlsClientCertificateBoundAccessTokens());
        assertNull(openIDProviderMetadata.getMtlsAliases());
        enableMtls();
        openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertTrue(openIDProviderMetadata.getTlsClientCertificateBoundAccessTokens());
        assertNotNull(openIDProviderMetadata.getMtlsAliases());
        assertTrue("revocation endpoint is required", openIDProviderMetadata.getMtlsAliases().getRevocationEndpoint().contains("revoke"));
        assertTrue("registration endpoint is required", openIDProviderMetadata.getMtlsAliases().getRegistrationEndpoint().contains("register"));
        assertTrue("userinfo endpoint is required", openIDProviderMetadata.getMtlsAliases().getUserinfoEndpoint().contains("userinfo"));
        assertTrue("token endpoint is required", openIDProviderMetadata.getMtlsAliases().getTokenEndpoint().contains("token"));
        assertTrue("authorization endpoint is required", openIDProviderMetadata.getMtlsAliases().getAuthorizationEndpoint().contains("authorize"));
        assertTrue("par endpoint is required", openIDProviderMetadata.getMtlsAliases().getParEndpoint().contains("par"));
        assertTrue("endSession endpoint is required", openIDProviderMetadata.getMtlsAliases().getEndSessionEndpoint().contains("logout"));
        assertTrue("introspection endpoint is required", openIDProviderMetadata.getMtlsAliases().getIntrospectionEndpoint().contains("introspect"));
    }

    @Test
    public void shouldContainsNotCIBAMetadata() {
        when(domain.useCiba()).thenReturn(false);
        final OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertFalse(openIDProviderMetadata.isBackchannelUserCodeSupported());
        assertNull(openIDProviderMetadata.getBackchannelAuthenticationEndpoint());
        assertNull(openIDProviderMetadata.getBackchannelAuthenticationSigningAlg());
        assertNull(openIDProviderMetadata.getBackchannelTokenDeliveryModesSupported());
    }

    @Test
    public void shouldContainsCIBAMetadata() {
        when(domain.useCiba()).thenReturn(true);
        final OpenIDProviderMetadata openIDProviderMetadata = openIDDiscoveryService.getConfiguration("/");
        assertFalse(openIDProviderMetadata.isBackchannelUserCodeSupported());
        assertTrue("ciba auth endpoint is required", openIDProviderMetadata.getBackchannelAuthenticationEndpoint().contains("ciba/authenticate"));
        assertNotNull(openIDProviderMetadata.getBackchannelAuthenticationSigningAlg());
        assertTrue(openIDProviderMetadata.getBackchannelAuthenticationSigningAlg().containsAll(JWAlgorithmUtils.getSupportedBackchannelAuthenticationSigningAl()));
        assertNotNull(openIDProviderMetadata.getBackchannelTokenDeliveryModesSupported());
        assertTrue(openIDProviderMetadata.getBackchannelTokenDeliveryModesSupported().containsAll(List.of(CIBADeliveryMode.POLL)));
    }

}
