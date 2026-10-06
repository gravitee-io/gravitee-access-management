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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.service.DomainDataPlane;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.core.Future;
import io.vertx.ext.auth.webauthn.WebAuthn;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class WebAuthnRegisterCredentialsEndpointTest extends RxWebTestBase {

    private static final String OTP_FACTOR_ID = "otp-factor";

    @Mock
    private Domain domain;
    @Mock
    private DomainDataPlane domainDataPlane;
    @Mock
    private FactorManager factorManager;
    @Mock
    private WebAuthn webAuthn;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        lenient().when(domainDataPlane.getDomain()).thenReturn(domain);
        lenient().when(webAuthn.createCredentialsOptions(any(JsonObject.class)))
                .thenReturn(Future.succeededFuture(new JsonObject().put("challenge", "challenge").put("user", new JsonObject())));
        Factor otp = new Factor();
        otp.setFactorType(FactorType.OTP);
        lenient().when(factorManager.getFactor(OTP_FACTOR_ID)).thenReturn(otp);
    }

    @Test
    public void shouldRejectCredentialOptions_whenUserHasActivatedFactorAndIsNotStronglyAuthenticated() throws Exception {
        givenRoute(userWithActivatedFactor(OTP_FACTOR_ID), false);

        testRequest(HttpMethod.POST, "/webauthn/register/credentials", sendRegisterRequest(), 403, "Forbidden", null);

        verify(webAuthn, never()).createCredentialsOptions(any(JsonObject.class));
    }

    @Test
    public void shouldReturnCredentialOptions_whenUserIsStronglyAuthenticated() throws Exception {
        givenRoute(userWithActivatedFactor(OTP_FACTOR_ID), true);

        testRequest(HttpMethod.POST, "/webauthn/register/credentials", sendRegisterRequest(), 200, "OK", null);
    }

    @Test
    public void shouldReturnCredentialOptions_whenUserHasNoFactor() throws Exception {
        givenRoute(new User(), false);

        testRequest(HttpMethod.POST, "/webauthn/register/credentials", sendRegisterRequest(), 200, "OK", null);
    }

    private void givenRoute(User endUser, boolean strongAuthCompleted) {
        router.route(HttpMethod.POST, "/webauthn/register/credentials")
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create())
                .handler(rc -> {
                    if (strongAuthCompleted) {
                        rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
                    }
                    RoutingContextHelper.setUser(rc, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    rc.next();
                })
                .handler(new WebAuthnRegisterCredentialsEndpoint(domainDataPlane, factorManager, webAuthn));
    }

    private static User userWithActivatedFactor(String factorId) {
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId(factorId);
        enrolledFactor.setStatus(FactorStatus.ACTIVATED);
        User user = new User();
        user.setId("user-id");
        user.setFactors(List.of(enrolledFactor));
        return user;
    }

    private Consumer<HttpClientRequest> sendRegisterRequest() {
        return req -> {
            req.headers().set("content-type", "application/json");
            req.setChunked(true);
            req.write(Buffer.buffer(new JsonObject().put("name", "username").put("displayName", "User").encode()));
        };
    }
}
