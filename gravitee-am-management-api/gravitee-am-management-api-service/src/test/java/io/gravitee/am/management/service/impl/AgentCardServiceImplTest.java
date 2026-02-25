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
package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.exception.AgentCardFetchException;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class AgentCardServiceImplTest {

    @InjectMocks
    private AgentCardServiceImpl agentCardService;

    @Mock
    private WebClient client;

    @Mock
    private HttpRequest httpRequest;

    @Mock
    private HttpResponse httpResponse;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(agentCardService, "objectMapper", objectMapper);
    }

    @Test
    public void shouldFetchAgentCard_success() throws InterruptedException {
        final String url = "https://example.com/.well-known/agent-card.json";
        final String json = "{\"name\":\"Test Agent\",\"version\":\"1.0\"}";

        when(client.getAbs(url)).thenReturn(httpRequest);
        when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.bodyAsString()).thenReturn(json);

        TestObserver<String> observer = agentCardService.fetchAgentCard(url).test();
        observer.await(2, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(json);
    }

    @Test
    public void shouldRejectLocalhost() throws InterruptedException {
        TestObserver<String> observer = agentCardService.fetchAgentCard("http://localhost/card.json").test();
        observer.await(2, TimeUnit.SECONDS);
        observer.assertError(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectPrivateIp_10x() throws InterruptedException {
        TestObserver<String> observer = agentCardService.fetchAgentCard("http://10.0.0.1/card").test();
        observer.await(2, TimeUnit.SECONDS);
        observer.assertError(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectPrivateIp_192168() throws InterruptedException {
        TestObserver<String> observer = agentCardService.fetchAgentCard("http://192.168.1.1/card").test();
        observer.await(2, TimeUnit.SECONDS);
        observer.assertError(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectOversizedResponse() throws InterruptedException {
        final String url = "https://example.com/card.json";
        final String oversizedBody = "x".repeat(512 * 1024 + 1);

        when(client.getAbs(url)).thenReturn(httpRequest);
        when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.bodyAsString()).thenReturn(oversizedBody);

        TestObserver<String> observer = agentCardService.fetchAgentCard(url).test();
        observer.await(2, TimeUnit.SECONDS);
        observer.assertError(AgentCardFetchException.class);
    }

    @Test
    public void shouldRejectInvalidJson() throws InterruptedException {
        final String url = "https://example.com/card.json";

        when(client.getAbs(url)).thenReturn(httpRequest);
        when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.bodyAsString()).thenReturn("not-valid-json{{{{");

        TestObserver<String> observer = agentCardService.fetchAgentCard(url).test();
        observer.await(2, TimeUnit.SECONDS);
        observer.assertError(AgentCardFetchException.class);
    }

    @Test
    public void shouldRejectNonOkStatus() throws InterruptedException {
        final String url = "https://example.com/card.json";

        when(client.getAbs(url)).thenReturn(httpRequest);
        when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
        when(httpRequest.rxSend()).thenReturn(Single.just(httpResponse));
        when(httpResponse.statusCode()).thenReturn(500);

        TestObserver<String> observer = agentCardService.fetchAgentCard(url).test();
        observer.await(2, TimeUnit.SECONDS);
        observer.assertError(AgentCardFetchException.class);
    }
}
