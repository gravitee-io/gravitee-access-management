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
package io.gravitee.am.authdevice.notifier.cibafederation.provider.spring;

import io.gravitee.am.service.http.WebClientBuilder;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CibaFederationWebClientOptionsTest {

    @Test
    void bounds_upstream_calls_with_the_gateway_http_client_timeout() {
        WebClientOptions options = CibaFederationProviderSpringConfiguration.webClientOptions(
                new MockEnvironment().withProperty("httpClient.timeout", "7000"));

        assertEquals(7000, options.getConnectTimeout());
        assertEquals(7000, options.getIdleTimeout());
        assertEquals(TimeUnit.MILLISECONDS, options.getIdleTimeoutUnit());
        assertEquals("Gravitee.io-AM-CIBA-Federation/1", options.getUserAgent());
    }

    @Test
    void defaults_to_ten_seconds() {
        WebClientOptions options = CibaFederationProviderSpringConfiguration.webClientOptions(new MockEnvironment());

        assertEquals(10000, options.getConnectTimeout());
        assertEquals(10000, options.getIdleTimeout());
        assertEquals(TimeUnit.MILLISECONDS, options.getIdleTimeoutUnit());
    }

    @Test
    void federation_web_client_is_built_with_the_bounded_options() {
        CibaFederationProviderSpringConfiguration configuration = new CibaFederationProviderSpringConfiguration();
        Vertx vertx = mock(Vertx.class);
        ReflectionTestUtils.setField(configuration, "vertx", vertx);
        ReflectionTestUtils.setField(configuration, "environment", new MockEnvironment().withProperty("httpClient.timeout", "7000"));
        WebClientBuilder builder = mock(WebClientBuilder.class);
        ArgumentCaptor<WebClientOptions> options = ArgumentCaptor.forClass(WebClientOptions.class);

        configuration.cibaFederationWebClient(builder);

        verify(builder).createWebClient(same(vertx), options.capture());
        assertEquals(7000, options.getValue().getConnectTimeout());
        assertEquals(7000, options.getValue().getIdleTimeout());
    }
}
