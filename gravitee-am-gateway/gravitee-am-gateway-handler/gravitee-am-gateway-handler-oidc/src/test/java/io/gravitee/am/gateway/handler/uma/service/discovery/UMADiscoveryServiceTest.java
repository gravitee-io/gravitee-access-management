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
package io.gravitee.am.gateway.handler.uma.service.discovery;

import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.uma.service.discovery.impl.UMADiscoveryServiceImpl;
import io.gravitee.am.model.Domain;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UMADiscoveryServiceTest {

    @InjectMocks
    private UMADiscoveryService discoveryService = new UMADiscoveryServiceImpl();

    @Mock
    private OpenIDDiscoveryService oidcDiscoveryService;

    @Mock
    private Domain domain;

    private static final String DOMAIN_PATH = "unit-test";
    private static final String BASE_PATH = "http://basepath";

    @Before
    public void setUp() {
        when(domain.getPath()).thenReturn(DOMAIN_PATH);
        when(oidcDiscoveryService.getConfiguration(BASE_PATH)).thenReturn(defaultMetadata());
    }

    private static OpenIDProviderMetadata defaultMetadata() {
        OpenIDProviderMetadata mock = new OpenIDProviderMetadata();
        mock.setIssuer("issuer");
        mock.setResponseTypesSupported(Arrays.asList("a","b"));
        mock.setTokenEndpoint("tokenEndpoint");
        return mock;
    }

    @Test
    public void getConfiguration() {
        UMAProviderMetadata result = discoveryService.getConfiguration(BASE_PATH);

        assertNotNull("Metadata are expected", result);

        //Checking metadata coming from oauth2 / oidc
        assertEquals("issuer", result.getIssuer());
        assertEquals("tokenEndpoint", result.getTokenEndpoint());
        assertNotNull(result.getResponseTypesSupported());
        assertTrue(result.getResponseTypesSupported().containsAll(Arrays.asList("a","b")));

        //Checking UMA metadata
        assertNotNull("resource_set is required", result.getResourceRegistrationEndpoint());
        assertTrue(result.getResourceRegistrationEndpoint().startsWith(BASE_PATH));
        assertNotNull("permission endpoint is required", result.getPermissionEndpoint());
        assertTrue(result.getPermissionEndpoint().startsWith(BASE_PATH));
        assertNotNull("uma profile metadata is required",result.getUmaProfilesSupported());
        assertTrue(result.getUmaProfilesSupported().isEmpty());
        //Currently not supported, but we can control if metadata is informed anyway
        assertNotNull("claims gathering endpoint", result.getClaimsInteractionEndpoint());
    }
}
