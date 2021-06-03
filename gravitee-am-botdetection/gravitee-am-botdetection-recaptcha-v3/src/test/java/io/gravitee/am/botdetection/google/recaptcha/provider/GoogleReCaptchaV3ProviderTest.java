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
package io.gravitee.am.botdetection.google.recaptcha.provider;

import io.gravitee.am.botdetection.api.BotDetectionContext;
import io.gravitee.am.botdetection.google.recaptcha.GoogleReCaptchaV3Configuration;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.observers.TestObserver;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.impl.ClientPhase;
import io.vertx.ext.web.client.impl.WebClientInternal;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class GoogleReCaptchaV3ProviderTest {

    public static final String TOKEN_PARAM_NAME = "X-TOKEN";
    public static final String URL = "https://www.google.com/recaptcha/api/siteverify";

    protected WebClient client = WebClient.wrap(Vertx.vertx().createHttpClient());

    @Mock
    private HttpResponse httpResponse;

    @Mock
    private GoogleReCaptchaV3Configuration configuration;

    @InjectMocks
    private GoogleReCaptchaV3Provider cut;

    @Before
    public void init() {
        ((WebClientInternal) client.getDelegate()).addInterceptor(event -> {

            if (event.phase() == ClientPhase.PREPARE_REQUEST) {
                // By pass send request and jump directly to dispatch phase with the mocked http response.
                event.dispatchResponse(httpResponse);
            }

            event.next();
        });
        cut.setClient(client);

        when(configuration.getTokenParameterName()).thenReturn(TOKEN_PARAM_NAME);
        when(configuration.getServiceUrl()).thenReturn(URL);
    }

    @Test
    public void shouldNoValidate_MissingToken() {
        final TestObserver<Boolean> testCall = cut.validate(new BotDetectionContext("plugin_id", null, null)).test();

        testCall.awaitTerminalEvent();
        testCall.assertNoErrors();
        testCall.assertValue(false);
    }

    @Test
    public void shouldValidate() {
        MultiMap multiMap = new MultiMap(new HeadersMultiMap());
        multiMap.add(TOKEN_PARAM_NAME, "token-value");

        when(configuration.getMinScore()).thenReturn(0.5f);

        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject()
                        .put("success", true)
                        .put("score", 0.5f)
                );

        final TestObserver<Boolean> testCall = cut.validate(new BotDetectionContext("plugin_id", multiMap, null)).test();

        testCall.awaitTerminalEvent();
        testCall.assertNoErrors();
        testCall.assertValue(true);
    }

    @Test
    public void shouldNotValidate_LowScore() {
        MultiMap multiMap = new MultiMap(new HeadersMultiMap());
        multiMap.add(TOKEN_PARAM_NAME, "token-value");

        when(configuration.getMinScore()).thenReturn(0.5f);

        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject()
                        .put("success", true)
                        .put("score", 0.4f)
                );

        final TestObserver<Boolean> testCall = cut.validate(new BotDetectionContext("plugin_id", multiMap, null)).test();

        testCall.awaitTerminalEvent();
        testCall.assertNoErrors();
        testCall.assertValue(false);
    }

    @Test
    public void shouldNotValidate_NotSuccessful() {
        MultiMap multiMap = new MultiMap(new HeadersMultiMap());
        multiMap.add(TOKEN_PARAM_NAME, "token-value");

        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.OK_200);
        when(httpResponse.bodyAsJsonObject())
                .thenReturn(new JsonObject()
                        .put("success", false)
                        .put("score", 0.8f)
                );

        final TestObserver<Boolean> testCall = cut.validate(new BotDetectionContext("plugin_id", multiMap, null)).test();

        testCall.awaitTerminalEvent();
        testCall.assertNoErrors();
        testCall.assertValue(false);
    }

    @Test
    public void shouldNotValidate_RequestError() {
        MultiMap multiMap = new MultiMap(new HeadersMultiMap());
        multiMap.add(TOKEN_PARAM_NAME, "token-value");

        when(httpResponse.statusCode())
                .thenReturn(HttpStatusCode.BAD_REQUEST_400);

        final TestObserver<Boolean> testCall = cut.validate(new BotDetectionContext("plugin_id", multiMap, null)).test();

        testCall.awaitTerminalEvent();
        testCall.assertNoErrors();
        testCall.assertValue(false);
    }

}
