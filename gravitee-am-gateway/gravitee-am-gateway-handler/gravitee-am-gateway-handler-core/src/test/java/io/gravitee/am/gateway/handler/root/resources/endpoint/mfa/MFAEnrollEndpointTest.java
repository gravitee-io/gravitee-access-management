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
package io.gravitee.am.gateway.handler.root.resources.endpoint.mfa;

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.ruleengine.RuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MFAEnrollEndpointTest extends RxWebTestBase {

    private static final String REQUEST_PATH = "/mfa/enroll";
    private static final String FACTOR_ID = "factor-id";
    private static final String SHARED_SECRET = "5DPI7NJ2Y7WBQEQV";
    private static final String BAR_CODE = "data:image/png;base64,barcode";

    @Mock
    private Domain domain;

    @Mock
    private ThymeleafTemplateEngine templateEngine;

    @Mock
    private FactorManager factorManager;
    @Mock
    private ApplicationContext applicationContext;
    private MFAEnrollEndpoint mfaEnrollEndpoint;
    @Mock
    private RuleEngine ruleEngine;
    private LocalSessionStore localSessionStore;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mfaEnrollEndpoint = new MFAEnrollEndpoint(factorManager, templateEngine, domain, applicationContext, ruleEngine);
        localSessionStore = LocalSessionStore.create(vertx);

        localSessionStore = LocalSessionStore.create(vertx);
        router.route()
                .handler(SessionHandler.create(localSessionStore))
                .handler(BodyHandler.create());
    }

    @Test
    public void shouldNotRenderPage_noUser() throws Exception {
        router.route(REQUEST_PATH)
                .handler(mfaEnrollEndpoint)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.GET,
                REQUEST_PATH,
                401,
                "Unauthorized");
    }

    @Test
    public void shouldRenderPage_nominalCase() throws Exception {
        when(templateEngine.render(anyMap(), any())).thenReturn(Single.just(Buffer.buffer()));

        router.route(REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    Client client = new Client();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings("factor-id")));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint)
                .handler(rc -> rc.response().end());

        Enrollment enrollment = mock(Enrollment.class);
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.enroll(any(FactorContext.class))).thenReturn(Single.just(enrollment));
        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factor.getId()).thenReturn("factor-id");
        when(factorManager.get(any())).thenReturn(factorProvider);
        when(factorManager.getFactor(any())).thenReturn(factor);

        testRequest(HttpMethod.GET,
                REQUEST_PATH,
                200,
                "OK");
    }

    @Test
    public void shouldNotRenderPageWhenFactorEnrolled() throws Exception {
        final var ENROLL_FACTOR_ID = UUID.randomUUID().toString();
        router.route(REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings(ENROLL_FACTOR_ID)));

                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    EnrolledFactorSecurity enrolledFactorSecurity = new EnrolledFactorSecurity();
                    enrolledFactor.setFactorId(ENROLL_FACTOR_ID);
                    enrolledFactor.setStatus(FactorStatus.ACTIVATED);
                    enrolledFactor.setSecurity(enrolledFactorSecurity);
                    user.setFactors(Collections.singletonList(enrolledFactor));

                    Client client = new Client();
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint)
                .handler(rc -> rc.response().end());

        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.getFactor(any())).thenReturn(factor);

        testRequest(HttpMethod.GET,
                REQUEST_PATH,
                302,
                "Found");
    }

    @Test
    public void shouldRenderPage_displayOnlyTheAlternativeId() throws Exception {
        String emailFactorId = "factor-id";
        String smsFactorId = "other-factor-id";
        router.route(REQUEST_PATH)
                .handler(ctx -> {
                    User user = new User();
                    Client client = new Client();
                    client.setFactorSettings(getFactorSettings(emailFactorId, smsFactorId));
                    client.setFactors(Set.of(emailFactorId, smsFactorId));
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.session().put(ConstantKeys.ALTERNATIVE_FACTOR_ID_KEY, emailFactorId);
                    ctx.next();
                })
                .handler(checkFactorList(mfaEnrollEndpoint))
                .handler(rc -> rc.response().end());

        Enrollment enrollment = mock(Enrollment.class);
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.enroll(any(FactorContext.class))).thenReturn(Single.just(enrollment));
        Factor emailFactor = mock(Factor.class);
        when(emailFactor.getId()).thenReturn(emailFactorId);
        when(emailFactor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.get(emailFactorId)).thenReturn(factorProvider);
        when(factorManager.getFactor(emailFactorId)).thenReturn(emailFactor);

        Factor smsFactor = mock(Factor.class);
        when(smsFactor.getId()).thenReturn(smsFactorId);
        when(smsFactor.getFactorType()).thenReturn(FactorType.SMS);
        when(factorManager.get(smsFactorId)).thenReturn(factorProvider);
        when(factorManager.getFactor(smsFactorId)).thenReturn(smsFactor);

        testRequest(HttpMethod.GET,
                REQUEST_PATH,
                200,
                "OK");
    }

    @Test
    public void shouldReuseSharedSecretFromSession() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.generateQrCode(any(), any())).thenReturn(Maybe.just(BAR_CODE));

        renderPageWithSession(factorProvider, session -> {
            session.put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, FACTOR_ID);
            session.put(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY, SHARED_SECRET);
        });

        ArgumentCaptor<EnrolledFactor> captor = ArgumentCaptor.forClass(EnrolledFactor.class);
        verify(factorProvider).generateQrCode(any(), captor.capture());
        assertEquals(SHARED_SECRET, captor.getValue().getSecurity().getValue());
        verify(factorProvider, never()).enroll(any(FactorContext.class));
    }

    @Test
    public void shouldGenerateNewSharedSecret_whenSessionHoldsNone() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.enroll(any(FactorContext.class))).thenReturn(Single.just(mock(Enrollment.class)));

        renderPageWithSession(factorProvider, session -> {
        });

        verify(factorProvider).enroll(any(FactorContext.class));
        verify(factorProvider, never()).generateQrCode(any(), any());
    }

    @Test
    public void shouldGenerateNewSharedSecret_whenSessionSecretBelongsToAnotherFactor() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.enroll(any(FactorContext.class))).thenReturn(Single.just(mock(Enrollment.class)));

        renderPageWithSession(factorProvider, session -> {
            session.put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, "another-factor-id");
            session.put(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY, SHARED_SECRET);
        });

        verify(factorProvider).enroll(any(FactorContext.class));
        verify(factorProvider, never()).generateQrCode(any(), any());
    }

    @Test
    public void shouldClearEnrollmentCompletedFlag() throws Exception {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.enroll(any(FactorContext.class))).thenReturn(Single.just(mock(Enrollment.class)));

        AtomicReference<Object> flagWhenRendered = new AtomicReference<>();
        renderPageWithSession(factorProvider,
                session -> session.put(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY, true),
                ctx -> flagWhenRendered.set(ctx.session().get(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY)));

        assertNull(flagWhenRendered.get());
    }

    private void renderPageWithSession(FactorProvider factorProvider, Consumer<Session> sessionSetup) throws Exception {
        renderPageWithSession(factorProvider, sessionSetup, ctx -> {
        });
    }

    /**
     * Renders the enroll page for a single OTP factor, letting the caller seed the session first and
     * inspect the routing context at the moment the template is rendered.
     */
    private void renderPageWithSession(FactorProvider factorProvider,
                                       Consumer<Session> sessionSetup,
                                       Consumer<RoutingContext> onRender) throws Exception {
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn(FACTOR_ID);
        when(factor.getFactorType()).thenReturn(FactorType.OTP);
        when(factorManager.get(FACTOR_ID)).thenReturn(factorProvider);
        when(factorManager.getFactor(FACTOR_ID)).thenReturn(factor);

        router.route(REQUEST_PATH)
                .handler(ctx -> {
                    Client client = new Client();
                    FactorSettings factorSettings = new FactorSettings();
                    factorSettings.setApplicationFactors(List.of(getApplicationFactorSettings(FACTOR_ID)));
                    client.setFactorSettings(factorSettings);
                    ctx.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new User())));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    sessionSetup.accept(ctx.session());
                    doAnswer(answer -> {
                        onRender.accept(ctx);
                        return Single.just(Buffer.buffer());
                    }).when(templateEngine).render(Mockito.<Map<String, Object>>any(), Mockito.any());
                    ctx.next();
                })
                .handler(mfaEnrollEndpoint)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.GET, REQUEST_PATH, 200, "OK");
    }

    private static FactorSettings getFactorSettings(String emailFactor, String smsFactor) {
        ApplicationFactorSettings appEmailFactor = getApplicationFactorSettings(emailFactor);
        appEmailFactor.setSelectionRule("");

        ApplicationFactorSettings appSmsFactor = getApplicationFactorSettings(smsFactor);
        appSmsFactor.setSelectionRule("");


        FactorSettings factorSettings = new FactorSettings();
        factorSettings.setDefaultFactorId("default-factor-id");
        factorSettings.setApplicationFactors(List.of(appEmailFactor, appSmsFactor));
        return factorSettings;
    }

    private Handler<RoutingContext> checkFactorList(Handler handler) {
        return routingContext -> {
            doAnswer(answer -> {
                assertTrue(((List) routingContext.get("factors")).size() == 1);
                return Single.just(Buffer.buffer());
            }).when(templateEngine).render(Mockito.<Map<String, Object>>any(), Mockito.any());
            handler.handle(routingContext);
        };
    }


    private static ApplicationFactorSettings getApplicationFactorSettings(String ENROLL_FACTOR_ID) {
        ApplicationFactorSettings applicationFactorSettings = new ApplicationFactorSettings();
        applicationFactorSettings.setId(ENROLL_FACTOR_ID);
        return applicationFactorSettings;
    }

}
