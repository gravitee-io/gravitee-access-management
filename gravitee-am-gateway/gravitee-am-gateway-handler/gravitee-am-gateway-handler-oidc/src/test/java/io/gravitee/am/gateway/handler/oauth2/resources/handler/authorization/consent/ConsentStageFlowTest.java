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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper.setUser;

public class ConsentStageFlowTest extends RxWebTestBase {

    private final Map<String, Object> sessionSeed = new HashMap<>();
    private final Map<String, Object> contextSeed = new HashMap<>();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        Client client = new Client();
        client.setClientId("client-id");
        User user = new User();
        user.setId("user-id");

        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1).handler(rc -> {
            sessionSeed.forEach((key, value) -> rc.session().put(key, value));
            contextSeed.forEach(rc::put);
            setUser(rc, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            rc.put(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY, new AuthorizationRequest());
            rc.next();
        });
        router.route(HttpMethod.GET, "/oauth/consent")
                .handler(new UserConsentPrepareContextHandler())
                .handler(ConsentFlowHandler.preConsent(flow("pre-consent")))
                .handler(new ConsentStageHandler(screen("scopes"), screen("authorization-details")));
        router.route(HttpMethod.POST, "/oauth/consent")
                .handler(new UserConsentPrepareContextHandler())
                .handler(ConsentFlowHandler.postConsent(flow("post-consent")))
                .handler(screen("submitted"));
    }

    @Test
    public void shouldServeTheScopeScreenFirst() throws Exception {
        expect(HttpMethod.GET, "scopes", "pre-consent");
    }

    @Test
    public void shouldServeTheDetailScreenWhenPending() throws Exception {
        sessionSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY, true);

        expect(HttpMethod.GET, "authorization-details", "pre-consent");
    }

    @Test
    public void shouldNotRunPreConsentAgainAfterTheScopeScreen() throws Exception {
        sessionSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY, true);
        sessionSeed.put(ConstantKeys.USER_CONSENT_COMPLETED_KEY, true);

        expect(HttpMethod.GET, "authorization-details", null);
    }

    @Test
    public void shouldRunPostConsentAfterTheLastSubmission() throws Exception {
        expect(HttpMethod.POST, "submitted", "post-consent");
    }

    @Test
    public void shouldNotRunPostConsentWhenTheDetailScreenComesNext() throws Exception {
        contextSeed.put(ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY, true);

        expect(HttpMethod.POST, "submitted", null);
    }

    private void expect(HttpMethod method, String screen, String flow) throws Exception {
        testRequest(method, "/oauth/consent", null, response -> assertEquals(flow, response.getHeader("X-Flow")), 200, "OK", screen);
    }

    private static io.vertx.core.Handler<RoutingContext> flow(String name) {
        return rc -> {
            rc.response().putHeader("X-Flow", name);
            rc.next();
        };
    }

    private static io.vertx.core.Handler<RoutingContext> screen(String name) {
        return rc -> rc.response().end(name);
    }
}
