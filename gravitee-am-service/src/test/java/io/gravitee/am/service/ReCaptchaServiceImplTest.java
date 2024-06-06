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
package io.gravitee.am.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.impl.ReCaptchaServiceImpl;
import io.gravitee.node.api.configuration.Configuration;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.impl.ClientPhase;
import io.vertx.ext.web.client.impl.WebClientInternal;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ReCaptchaServiceImplTest {

    @InjectMocks
    private ReCaptchaServiceImpl reCaptchaService = new ReCaptchaServiceImpl();

    @Spy
    protected WebClient client = WebClient.wrap(Vertx.vertx().createHttpClient());

    @Mock
    protected HttpResponse httpResponse;

    @Mock
    private Configuration configuration;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void before() {
        ReflectionTestUtils.setField(reCaptchaService, "objectMapper", objectMapper);
        when(configuration.getProperty(eq("reCaptcha.serviceUrl"), anyString())).thenReturn("https://verif");
    }

    @Test
    public void isValidWhenDisabled() {
        when(configuration.getProperty("reCaptcha.enabled", Boolean.class, false)).thenReturn(false);

        TestObserver<Boolean> obs = reCaptchaService.isValid(null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(true);

        obs = reCaptchaService.isValid("").test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(true);

        obs = reCaptchaService.isValid("any").test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(true);
    }

    @Test
    public void isNotValidIfNoToken() {
        when(configuration.getProperty("reCaptcha.enabled", Boolean.class, false)).thenReturn(true);

        TestObserver<Boolean> obs = reCaptchaService.isValid(null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(false);

        obs = reCaptchaService.isValid("").test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(false);
    }

    @Test
    public void isValid() {
        when(configuration.getProperty("reCaptcha.minScore", Double.class, 0.5)).thenReturn(0.5);
        when(configuration.getProperty("reCaptcha.enabled", Boolean.class, false)).thenReturn(true);

        when(httpResponse.bodyAsString())
                .thenReturn(new JsonObject().put("success", true).put("score", 0.9d).toString());

        spyHttpRequest(client.post(eq("https://verif")));

        TestObserver<Boolean> obs = reCaptchaService.isValid("any").test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(true);
    }

    @Test
    public void isValidAboveMinScore() {
        when(configuration.getProperty("reCaptcha.minScore", Double.class, 0.5)).thenReturn(0.5);
        when(configuration.getProperty("reCaptcha.enabled", Boolean.class, false)).thenReturn(true);

        when(httpResponse.bodyAsString())
                .thenReturn(new JsonObject().put("success", true).put("score", 1.0d).toString());

        spyHttpRequest(client.post(eq("https://verif")));

        TestObserver<Boolean> obs = reCaptchaService.isValid("any").test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(true);
    }

    @Test
    public void isNotValidBelowMinScore() {
        when(configuration.getProperty("reCaptcha.minScore", Double.class, 0.5)).thenReturn(0.5);
        when(configuration.getProperty("reCaptcha.enabled", Boolean.class, false)).thenReturn(true);

        when(httpResponse.bodyAsString())
                .thenReturn(new JsonObject().put("success", true).put("score", 0.4d).toString());

        spyHttpRequest(client.post(eq("https://verif")));

        TestObserver<Boolean> obs = reCaptchaService.isValid("any").test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(false);
    }

    @Test
    public void isNotValidNoSuccess() {
        when(configuration.getProperty("reCaptcha.enabled", Boolean.class, false)).thenReturn(true);

        when(httpResponse.bodyAsString())
                .thenReturn(new JsonObject().put("success", false).put("score", 0.0d).toString());

        spyHttpRequest(client.post(eq("https://verif")));

        TestObserver<Boolean> obs = reCaptchaService.isValid("any").test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(false);
    }

    @Test
    public void isNotEnabled() {
        when(configuration.getProperty("reCaptcha.enabled", Boolean.class, false)).thenReturn(false);
        assertFalse(reCaptchaService.isEnabled());
    }

    @Test
    public void getSiteKeyNullByDefault() {

        assertNull(reCaptchaService.getSiteKey());
    }

    @Test
    public void getSiteKey() {
        when(configuration.getProperty("reCaptcha.siteKey")).thenReturn("test");

        assertEquals("test", reCaptchaService.getSiteKey());
    }

    @Before
    public void beforeEach() {
        ((WebClientInternal) client.getDelegate()).addInterceptor(event -> {

            if (event.phase() == ClientPhase.PREPARE_REQUEST) {
                // By pass send request and jump directly to dispatch phase with the mocked http response.
                event.dispatchResponse(httpResponse);
            }

            event.next();
        });

        verify(client).getDelegate();
    }

    protected Single<HttpRequest> spyHttpRequest(HttpRequest httpRequest) {


        CompletableFuture<HttpRequest> spyHttpRequest = new CompletableFuture<>();

        when(httpRequest).thenAnswer(invocationOnMock -> {
            spyHttpRequest.complete((HttpRequest) spy(invocationOnMock.callRealMethod()));
            return spyHttpRequest.get();
        });

        return Single.fromFuture(spyHttpRequest);
    }
}
