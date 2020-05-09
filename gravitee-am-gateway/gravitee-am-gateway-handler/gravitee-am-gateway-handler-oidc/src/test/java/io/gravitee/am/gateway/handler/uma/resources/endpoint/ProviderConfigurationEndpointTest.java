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
package io.gravitee.am.gateway.handler.uma.resources.endpoint;

import io.gravitee.am.gateway.handler.uma.service.discovery.UMADiscoveryService;
import io.gravitee.am.gateway.handler.uma.service.discovery.UMAProviderMetadata;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ProviderConfigurationEndpointTest {

    @Mock
    private UMADiscoveryService discoveryService;

    @Mock
    private RoutingContext context;

    @Mock
    private HttpServerResponse response;

    @Mock
    private HttpServerRequest request;

    @InjectMocks
    private ProviderConfigurationEndpoint endpoint = new ProviderConfigurationEndpoint(discoveryService);

    @Test
    public void success() {
        ArgumentCaptor<String> strCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> intCaptor = ArgumentCaptor.forClass(Integer.class);

        when(context.response()).thenReturn(response);
        when(response.putHeader(anyString(),anyString())).thenReturn(response);
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(context.request()).thenReturn(request);
        when(request.getHeader(anyString())).thenReturn(null);
        when(discoveryService.getConfiguration(anyString())).thenReturn(new UMAProviderMetadata().setResourceRegistrationEndpoint("RRE"));

        endpoint.handle(context);

        verify(response, times(1)).end(strCaptor.capture());
        verify(response, times(1)).setStatusCode(intCaptor.capture());
        verify(response, times(1)).putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        Assert.assertEquals("Should be ok", 200, intCaptor.getValue().intValue());
        Assert.assertTrue(strCaptor.getValue().contains("\"resource_registration_endpoint\" : \"RRE\""));
    }
}
