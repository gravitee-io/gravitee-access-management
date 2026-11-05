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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthorizationRequestConsentSessionHandlerTest extends RxWebTestBase {

    private static final String AUTHORIZE = "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&scope=openid&state=first";
    private static final List<String> CONSENT_KEYS = List.of(
            ConstantKeys.USER_CONSENT_COMPLETED_KEY,
            ConstantKeys.USER_CONSENT_APPROVED_KEY,
            ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY,
            ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PRESENTED_KEY,
            ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_COMPLETED_KEY,
            ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_APPROVED_KEY);

    private final Map<String, Object> sessionSeed = new HashMap<>();
    private AuthorizationRequest authorizationRequest;
    private String storedDigest;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1).handler(rc -> {
            sessionSeed.forEach((key, value) -> rc.session().put(key, value));
            authorizationRequest = new AuthorizationRequest();
            authorizationRequest.setApproved(true);
            rc.put(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
            rc.next();
        });
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(new AuthorizationRequestConsentSessionHandler())
                .handler(rc -> {
                    storedDigest = rc.session().get(ConstantKeys.CONSENT_REQUEST_DIGEST_KEY);
                    rc.response().end(String.valueOf(CONSENT_KEYS.stream().filter(key -> rc.session().get(key) != null).count()));
                });
    }

    @Test
    public void shouldKeepTheConsentStateOfTheSameRequest() throws Exception {
        givenConsentStateOf(AUTHORIZE);

        testRequest(HttpMethod.GET, AUTHORIZE, 200, "OK", String.valueOf(CONSENT_KEYS.size()));
        assertTrue(authorizationRequest.isApproved());
    }

    @Test
    public void shouldDiscardTheConsentStateOfAnotherRequest() throws Exception {
        givenConsentStateOf(AUTHORIZE);

        testRequest(HttpMethod.GET, AUTHORIZE.replace("state=first", "state=second"), 200, "OK", "0");
        assertFalse(authorizationRequest.isApproved());
    }

    @Test
    public void shouldDiscardTheConsentStateOfAnotherClient() throws Exception {
        givenConsentStateOf(AUTHORIZE);

        testRequest(HttpMethod.GET, AUTHORIZE.replace("client_id=client-id", "client_id=other-client"), 200, "OK", "0");
    }

    @Test
    public void shouldDiscardConsentStateNotBoundToARequest() throws Exception {
        CONSENT_KEYS.forEach(key -> sessionSeed.put(key, true));

        testRequest(HttpMethod.GET, AUTHORIZE, 200, "OK", "0");
        assertNotNull(storedDigest);
    }

    private void givenConsentStateOf(String uri) throws Exception {
        testRequest(HttpMethod.GET, uri, 200, "OK");
        sessionSeed.put(ConstantKeys.CONSENT_REQUEST_DIGEST_KEY, storedDigest);
        CONSENT_KEYS.forEach(key -> sessionSeed.put(key, true));
    }
}
