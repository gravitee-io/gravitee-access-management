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
package io.gravitee.am.gateway.handler.oauth2.resources.handler;

import io.gravitee.am.common.oidc.Prompt;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestMFAPromptHandler;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static io.gravitee.am.common.utils.ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationRequestMFAPromptHandlerTest extends RxWebTestBase {

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route()
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)));
    }

    @Test
    public void shouldNotPromptMFA_noAuthorizationRequest() throws Exception {
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(new AuthorizationRequestMFAPromptHandler())
                .handler(rc -> rc.response().end());

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/authorize/callback",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldNotPromptMFA_noPromptParameter() throws Exception {
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(routingContext -> {
                    routingContext.put(AUTHORIZATION_REQUEST_CONTEXT_KEY, new AuthorizationRequest());
                    routingContext.next();
                })
                .handler(new AuthorizationRequestMFAPromptHandler())
                .handler(rc -> rc.response().end());

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/authorize/callback",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldNotPromptMFA_mfaChallengeCompleted() throws Exception {
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(routingContext -> {
                    AuthorizationRequest authorizationRequest = new AuthorizationRequest();
                    authorizationRequest.setPrompts(Collections.singleton(Prompt.MFA_ENROLL));
                    routingContext.put(AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
                    routingContext.session().put(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY, true);
                    routingContext.session().put(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY, true);
                    routingContext.next();
                })
                .handler(new AuthorizationRequestMFAPromptHandler())
                .handler(rc -> rc.response().end());

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&prompt=mfa_enroll&client_id=client-id&redirect_uri=http://localhost:9999/authorize/callback",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldPromptMFA_mfaEnrollPage() throws Exception {
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(routingContext -> {
                    AuthorizationRequest authorizationRequest = new AuthorizationRequest();
                    authorizationRequest.setPrompts(Collections.singleton(Prompt.MFA_ENROLL));
                    routingContext.put(AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
                    routingContext.session().put(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY, false);
                    routingContext.next();
                })
                .handler(new AuthorizationRequestMFAPromptHandler())
                .handler(rc -> rc.response().end());

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&prompt=mfa_enroll&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/mfa/enroll"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldPromptMFA_mfaChallengePage() throws Exception {
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(routingContext -> {
                    AuthorizationRequest authorizationRequest = new AuthorizationRequest();
                    authorizationRequest.setPrompts(Collections.singleton(Prompt.MFA_ENROLL));
                    routingContext.put(AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
                    routingContext.session().put(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY, true);
                    routingContext.session().put(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY, false);
                    routingContext.next();
                })
                .handler(new AuthorizationRequestMFAPromptHandler())
                .handler(rc -> rc.response().end());

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&prompt=mfa_enroll&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/mfa/challenge"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
