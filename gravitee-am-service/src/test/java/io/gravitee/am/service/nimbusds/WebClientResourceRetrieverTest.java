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
package io.gravitee.am.service.nimbusds;

import com.nimbusds.jose.util.Resource;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.jwk.JWKSetFetcher;
import io.gravitee.am.service.jwk.JWKSetFetcher.JWKSetFetchResponse;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebClientResourceRetrieverTest {

    @Mock
    private JWKSetFetcher jwkSetFetcher;

    private WebClientResourceRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new WebClientResourceRetriever(jwkSetFetcher);
    }

    @Test
    void shouldReturnResourceFromFetcher() throws Exception {
        URL url = new URL("https://example.com/jwks");
        Resource resource = new Resource("{\"keys\":[]}", "application/json");
        when(jwkSetFetcher.getKeys(url.toExternalForm()))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(new JWKSet(), resource)));

        Resource result = retriever.retrieveResource(url);

        assertThat(result).isSameAs(resource);
    }

    @Test
    void shouldReturnNullWhenFetcherEmpty() throws Exception {
        URL url = new URL("https://example.com/jwks");
        when(jwkSetFetcher.getKeys(url.toExternalForm())).thenReturn(Maybe.empty());

        assertThat(retriever.retrieveResource(url)).isNull();
    }

    @Test
    void shouldPropagateFetcherError() throws Exception {
        URL url = new URL("https://example.com/jwks");
        when(jwkSetFetcher.getKeys(url.toExternalForm()))
                .thenReturn(Maybe.error(new InvalidClientMetadataException("boom")));

        assertThatThrownBy(() -> retriever.retrieveResource(url))
                .isInstanceOf(InvalidClientMetadataException.class);
    }
}
