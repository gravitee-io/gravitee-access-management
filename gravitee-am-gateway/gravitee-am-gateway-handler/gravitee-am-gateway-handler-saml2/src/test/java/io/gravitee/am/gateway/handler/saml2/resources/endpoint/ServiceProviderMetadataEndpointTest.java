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
package io.gravitee.am.gateway.handler.saml2.resources.endpoint;

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.saml2.service.sp.ServiceProviderService;
import io.gravitee.am.identityprovider.api.Metadata;
import io.gravitee.am.service.exception.IdentityProviderMetadataNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ServiceProviderMetadataEndpointTest extends RxWebTestBase {

    @Mock
    private ServiceProviderService serviceProviderService;

    @InjectMocks
    private ServiceProviderMetadataEndpoint spMetadataEndpoint = new ServiceProviderMetadataEndpoint(serviceProviderService);

    @Override
    public void setUp() throws Exception {
        super.setUp();
        router.route(HttpMethod.GET, "/sp/metadata/:providerId")
                .handler(spMetadataEndpoint);
    }

    @Test
    public void shouldNotInvokeEndpoint_invalidProvider() throws Exception {
        when(serviceProviderService.metadata(eq("unknown-provider"), anyString())).thenReturn(Single.error(new IdentityProviderNotFoundException("unknown-provider")));

        testRequest(
                HttpMethod.GET, "/sp/metadata/unknown-provider",
                HttpStatusCode.NOT_FOUND_404, "Not Found");
    }

    @Test
    public void shouldNotInvokeEndpoint_invalidMetadata() throws Exception {
        when(serviceProviderService.metadata(eq("unknown-provider"), anyString())).thenReturn(Single.error(new IdentityProviderMetadataNotFoundException("unknown-provider")));

        testRequest(
                HttpMethod.GET, "/sp/metadata/unknown-provider",
                HttpStatusCode.NOT_FOUND_404, "Not Found");
    }

    @Test
    public void shouldInvokeEndpoint() throws Exception {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);
        Metadata metadata = mock(Metadata.class);
        when(metadata.getHeaders()).thenReturn(httpHeaders);
        when(metadata.getBody()).thenReturn("<xml></xml>");
        when(serviceProviderService.metadata(eq("provider-id"), anyString())).thenReturn(Single.just(metadata));

        testRequest(
                HttpMethod.GET,
                "/sp/metadata/provider-id",
                null,
                resp -> {
                    // check headers
                    assertEquals(MediaType.APPLICATION_XML, resp.headers().get(HttpHeaders.CONTENT_TYPE));
                    // check body
                    resp.bodyHandler(body -> {
                        assertEquals("<xml></xml>", body.toString());
                    });
                },
                HttpStatusCode.OK_200, "OK", null);
    }
}
