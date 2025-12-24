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
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URL;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebClientResourceRetrieverTest {

    @Mock
    private WebClient webClient;

    @Mock
    private HttpRequest<Buffer> httpRequest;

    @Mock
    private HttpResponse<Buffer> httpResponse;

    private WebClientResourceRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new WebClientResourceRetriever(webClient);
    }

    @Test
    void shouldRetrieveResourceSuccessfully_whenHttp200() throws Exception {
        // given
        URL url = new URL("https://example.com/jwks");
        String body = "{\"keys\":[]}";
        String contentType = "application/json";

        when(webClient.getAbs(url.toExternalForm())).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.bodyAsString()).thenReturn(body);
        when(httpResponse.getHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(contentType);

        // when
        Resource resource = retriever.retrieveResource(url);

        // then
        assertThat(resource).isNotNull();
        assertThat(resource.getContent()).isEqualTo(body);
        assertThat(resource.getContentType()).isEqualTo(contentType);
    }

    @Test
    void shouldThrowIOException_whenHttpStatusIsNot200() throws Exception {
        // given
        URL url = new URL("https://example.com/jwks");

        when(webClient.getAbs(url.toExternalForm())).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(404);
        when(httpResponse.bodyAsString()).thenReturn("Not Found");

        // when / then
        assertThatThrownBy(() -> retriever.retrieveResource(url))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(url.toString());
    }

    @Test
    void shouldWrapExceptionInIOException_whenWebClientFails() throws Exception {
        // given
        URL url = new URL("https://example.com/jwks");

        when(webClient.getAbs(url.toExternalForm())).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.error(new RuntimeException("Boom")));

        // when / then
        assertThatThrownBy(() -> retriever.retrieveResource(url))
                .isInstanceOf(IOException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
