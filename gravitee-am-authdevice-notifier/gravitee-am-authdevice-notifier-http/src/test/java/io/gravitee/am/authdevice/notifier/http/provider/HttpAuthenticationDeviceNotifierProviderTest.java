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

package io.gravitee.am.authdevice.notifier.http.provider;


import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import io.gravitee.am.authdevice.notifier.api.exception.DeviceNotificationException;
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationRequest;
import io.gravitee.am.authdevice.notifier.http.HttpAuthenticationDeviceNotifierConfiguration;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HttpAuthenticationDeviceNotifierProviderTest {

    public static final String NOTIFY_ENDPOINT = "/notify";
    @RegisterExtension
    private static WireMockExtension wiremock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().dynamicHttpsPort())
            .build();

    private static HttpAuthenticationDeviceNotifierConfiguration config = new HttpAuthenticationDeviceNotifierConfiguration();
    private static WebClient webClient;

    @BeforeAll
    public static void initWebClient() {
        webClient = WebClient.create(Vertx.vertx());
        config.setEndpoint("http://localhost:" + wiremock.getRuntimeInfo().getHttpPort() + NOTIFY_ENDPOINT);
    }

    @Test
    void notifier_client_should_provide_all_scopes() throws Exception {
        final var notificationRequest = new ADNotificationRequest();
        notificationRequest.setDeviceNotifierId(UUID.randomUUID().toString());
        notificationRequest.setScopes(new TreeSet<>(Set.of("email", "openid", "profile")));
        notificationRequest.setMessage(UUID.randomUUID().toString());
        notificationRequest.setSubject(UUID.randomUUID().toString());
        notificationRequest.setTransactionId("transaction_id");
        notificationRequest.setState("mystate");

        // The static DSL will be automatically configured for you
        wiremock.stubFor(post(NOTIFY_ENDPOINT)
                .withFormParam("scope", new EqualToPattern("email openid profile"))
                .willReturn(okJson("""
{ 
 "tid": "transaction_id", 
 "state": "mystate",
 "data": {
    "a": "avalue",
    "b": "bvalue"
 }
}
""")));

        var observer = new HttpAuthenticationDeviceNotifierProvider(webClient, config).notify(notificationRequest).test();
        observer.await(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(notifierResponse -> notifierResponse.getTransactionId().equals(notificationRequest.getTransactionId()));
        observer.assertValue(notifierResponse -> notifierResponse.getExtraData().containsKey("a") && notifierResponse.getExtraData().containsKey("b"));
    }

    @Test
    void notifier_should_reject_response_with_invalid_tid() throws Exception {
        final var notificationRequest = new ADNotificationRequest();
        notificationRequest.setDeviceNotifierId(UUID.randomUUID().toString());
        notificationRequest.setScopes(new TreeSet<>(Set.of("email", "openid", "profile")));
        notificationRequest.setMessage(UUID.randomUUID().toString());
        notificationRequest.setSubject(UUID.randomUUID().toString());
        notificationRequest.setTransactionId("transaction_id");
        notificationRequest.setState("mystate");

        // The static DSL will be automatically configured for you
        wiremock.stubFor(post(NOTIFY_ENDPOINT)
                .withFormParam("scope", new EqualToPattern("email openid profile"))
                .willReturn(okJson("""
{ 
 "tid": "unknown", 
 "state": "mystate",
 "data": {
    "a": "avalue",
    "b": "bvalue"
 }
}
""")));

        var observer = new HttpAuthenticationDeviceNotifierProvider(webClient, config).notify(notificationRequest).test();
        observer.await(10, TimeUnit.SECONDS);
        observer.assertError(DeviceNotificationException.class);
        observer.assertError(err -> err.getMessage().equals("Invalid device notification response"));
    }

    @Test
    void notifier_should_reject_response_with_invalid_state() throws Exception {
        final var notificationRequest = new ADNotificationRequest();
        notificationRequest.setDeviceNotifierId(UUID.randomUUID().toString());
        notificationRequest.setScopes(new TreeSet<>(Set.of("email", "openid", "profile")));
        notificationRequest.setMessage(UUID.randomUUID().toString());
        notificationRequest.setSubject(UUID.randomUUID().toString());
        notificationRequest.setTransactionId("transaction_id");
        notificationRequest.setState("mystate");

        // The static DSL will be automatically configured for you
        wiremock.stubFor(post(NOTIFY_ENDPOINT)
                .withFormParam("scope", new EqualToPattern("email openid profile"))
                .willReturn(okJson("""
{ 
 "tid": "transaction_id", 
 "state": "anotherstate",
 "data": {
    "a": "avalue",
    "b": "bvalue"
 }
}
""")));

        var observer = new HttpAuthenticationDeviceNotifierProvider(webClient, config).notify(notificationRequest).test();
        observer.await(10, TimeUnit.SECONDS);
        observer.assertError(DeviceNotificationException.class);
        observer.assertError(err -> err.getMessage().equals("Invalid device notification response"));
    }

    @Test
    void notifier_should_return_error_on_notOK_status() throws Exception {
        final var notificationRequest = new ADNotificationRequest();
        notificationRequest.setDeviceNotifierId(UUID.randomUUID().toString());
        notificationRequest.setScopes(new TreeSet<>(Set.of("email", "openid", "profile")));
        notificationRequest.setMessage(UUID.randomUUID().toString());
        notificationRequest.setSubject(UUID.randomUUID().toString());
        notificationRequest.setTransactionId("transaction_id");
        notificationRequest.setState("mystate");

        // The static DSL will be automatically configured for you
        wiremock.stubFor(post(NOTIFY_ENDPOINT)
                .withFormParam("scope", new EqualToPattern("email openid profile"))
                .willReturn(notFound()));

        var observer = new HttpAuthenticationDeviceNotifierProvider(webClient, config).notify(notificationRequest).test();
        observer.await(10, TimeUnit.SECONDS);
        observer.assertError(DeviceNotificationException.class);
        observer.assertError(err -> err.getMessage().equals("Device notification fails"));
    }
}
