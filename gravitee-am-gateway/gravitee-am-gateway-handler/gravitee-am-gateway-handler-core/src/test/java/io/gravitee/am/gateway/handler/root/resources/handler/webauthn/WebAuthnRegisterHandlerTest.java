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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DomainDataPlane;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.rxjava3.ext.auth.webauthn.WebAuthn;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WebAuthnRegisterHandlerTest extends RxWebTestBase {

    private static final String FIDO2_FACTOR_ID = "fido2-factor";
    private static final String OTP_FACTOR_ID = "otp-factor";
    private static final String CREDENTIAL_ID = "QAn8i7wToriAx38jzUrVw04Cxao_y285vX2CGwyFtO8";

    @Mock
    private Domain domain;
    @Mock
    private DomainDataPlane domainDataPlane;
    @Mock
    private UserService userService;
    @Mock
    private FactorManager factorManager;
    @Mock
    private CredentialGatewayService credentialService;
    @Mock
    private WebAuthn webAuthn;

    private final Map<String, Object> sessionAfterRegistration = new ConcurrentHashMap<>();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        lenient().when(domainDataPlane.getDomain()).thenReturn(domain);
        lenient().when(domain.getWebAuthnSettings()).thenReturn(new WebAuthnSettings());
        lenient().when(webAuthn.authenticate(any(Credentials.class))).thenReturn(Single.just(io.vertx.rxjava3.ext.auth.User.fromName("username")));

        Credential credential = new Credential();
        credential.setId("internal-id");
        credential.setCredentialId(CREDENTIAL_ID);
        lenient().when(credentialService.findByCredentialId(any(), any())).thenReturn(Flowable.just(credential));
        lenient().when(credentialService.update(any(), any(), any())).thenReturn(Single.just(credential));
        lenient().when(userService.upsertFactor(any(), any(), any())).thenReturn(Single.just(new User()));

        givenFactor(FIDO2_FACTOR_ID, FactorType.FIDO2);
        givenFactor(OTP_FACTOR_ID, FactorType.OTP);
    }

    @Test
    public void shouldRejectRegistration_whenUserHasActivatedFactorAndMfaChallengeNotCompleted() throws Exception {
        givenRoute(userWithActivatedFactor(OTP_FACTOR_ID), false);

        testRequest(HttpMethod.POST, "/webauthn/register", sendAssertion(), 403, "Forbidden", null);

        assertEquals(true, sessionAfterRegistration.get(ConstantKeys.USER_LOGIN_COMPLETED_KEY));
        assertNull(sessionAfterRegistration.get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY));
        assertNull(sessionAfterRegistration.get(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY));
        assertNull(sessionAfterRegistration.get(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY));
        verify(credentialService, never()).update(any(), any(), any());
        verify(userService, never()).upsertFactor(any(), any(), any());
    }

    @Test
    public void shouldRegisterAndEnrollFido2_whenUserHasNoFactor() throws Exception {
        givenRoute(new User(), false);

        testRequest(HttpMethod.POST, "/webauthn/register", sendAssertion(), 200, "OK", null);

        assertEquals(true, sessionAfterRegistration.get(ConstantKeys.STRONG_AUTH_COMPLETED_KEY));
        verify(userService).upsertFactor(any(), any(), any());
    }

    @Test
    public void shouldRegister_whenUserHasActivatedFactorAndAlreadyStronglyAuthenticated() throws Exception {
        givenRoute(userWithActivatedFactor(OTP_FACTOR_ID), true);

        testRequest(HttpMethod.POST, "/webauthn/register", sendAssertion(), 200, "OK", null);

        verify(userService).upsertFactor(any(), any(), any());
    }

    @Test
    public void shouldRegister_whenUserOnlyHasRecoveryCodes() throws Exception {
        givenFactor("recovery-factor", FactorType.RECOVERY_CODE);
        givenRoute(userWithActivatedFactor("recovery-factor"), false);

        testRequest(HttpMethod.POST, "/webauthn/register", sendAssertion(), 200, "OK", null);
    }

    @Test
    public void shouldRegister_whenEnrollingSelectedFido2Factor() throws Exception {
        givenRoute(userWithActivatedFactor(OTP_FACTOR_ID), false, FIDO2_FACTOR_ID);

        testRequest(HttpMethod.POST, "/webauthn/register", sendAssertion(), 200, "OK", null);

        verify(userService).upsertFactor(any(), any(), any());
    }

    private void givenRoute(User endUser, boolean strongAuthCompleted) {
        givenRoute(endUser, strongAuthCompleted, null);
    }

    private void givenRoute(User endUser, boolean strongAuthCompleted, String enrollingFactorId) {
        WebAuthnRegisterHandler handler = new WebAuthnRegisterHandler(userService, factorManager, domainDataPlane, webAuthn, credentialService);
        router.route(HttpMethod.POST, "/webauthn/register")
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create())
                .handler(rc -> {
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, "challenge");
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, "username");
                    rc.session().put(ConstantKeys.USER_LOGIN_COMPLETED_KEY, true);
                    if (strongAuthCompleted) {
                        rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
                    }
                    if (enrollingFactorId != null) {
                        rc.session().put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, enrollingFactorId);
                    }
                    rc.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, clientWithFactors(FIDO2_FACTOR_ID, OTP_FACTOR_ID));
                    rc.next();
                })
                .handler(handler)
                .handler(rc -> {
                    rc.session().data().forEach(sessionAfterRegistration::put);
                    rc.end();
                })
                .failureHandler(rc -> {
                    rc.session().data().forEach(sessionAfterRegistration::put);
                    rc.response().setStatusCode(rc.statusCode()).end();
                });
    }

    private void givenFactor(String factorId, FactorType type) {
        Factor factor = new Factor();
        factor.setId(factorId);
        factor.setFactorType(type);
        lenient().when(factorManager.get(factorId)).thenReturn(mock(FactorProvider.class));
        lenient().when(factorManager.getFactor(factorId)).thenReturn(factor);
    }

    private static Client clientWithFactors(String... factorIds) {
        Client client = new Client();
        FactorSettings factorSettings = new FactorSettings();
        factorSettings.setApplicationFactors(List.of(factorIds).stream().map(id -> {
            ApplicationFactorSettings settings = new ApplicationFactorSettings();
            settings.setId(id);
            return settings;
        }).toList());
        client.setFactorSettings(factorSettings);
        return client;
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

    private Consumer<HttpClientRequest> sendAssertion() {
        return req -> {
            req.headers().set("content-type", "application/x-www-form-urlencoded");
            req.setChunked(true);
            req.write(Buffer.buffer("assertion=%7B%22id%22%3A%22" + CREDENTIAL_ID + "%22%2C%22rawId%22%3A%22" + CREDENTIAL_ID
                    + "%22%2C%22response%22%3A%7B%7D%2C%22type%22%3A%22public-key%22%7D"));
        };
    }
}
