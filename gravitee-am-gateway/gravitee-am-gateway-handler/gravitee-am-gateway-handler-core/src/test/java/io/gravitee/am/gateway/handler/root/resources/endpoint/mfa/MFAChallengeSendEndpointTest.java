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
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.gravitee.am.service.DomainDataPlane;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MFAChallengeSendEndpointTest extends RxWebTestBase {

    @Mock
    private TemplateEngine templateEngine;
    @Mock
    private FactorManager factorManager;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private Domain domain;
    @Mock
    private DomainDataPlane domainDataPlane;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private AuditService auditService;

    private MFAChallengeSendEndpoint mfaChallengeSendEndpoint;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mfaChallengeSendEndpoint = new MFAChallengeSendEndpoint(factorManager, templateEngine, applicationContext,
                domainDataPlane, rateLimiterService, auditService);
        when(domain.getId()).thenReturn("id");
        when(domainDataPlane.getDomain()).thenReturn(domain);
    }

    private void awaitResponseEnd(SpyRoutingContext spyRoutingContext) {
        Completable completable = spyRoutingContext.ended()
                ? Completable.complete()
                : Completable.create(emitter -> spyRoutingContext.response().endHandler(v -> {
                    if (!emitter.isDisposed()) {
                        emitter.onComplete();
                    }
                }));
        TestObserver<Void> testObserver = completable.test();
        testObserver.awaitDone(20, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldResendCode_andReturnJsonSuccess() {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(factorProvider.useVariableFactorSecurity(any())).thenReturn(true);
        when(factorProvider.sendChallenge(any())).thenReturn(Completable.complete());
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factorId");
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factorManager.get("factorId")).thenReturn(factorProvider);
        when(factorManager.getFactor("factorId")).thenReturn(factor);

        SpyRoutingContext spyRoutingContext = new SpyRoutingContext();
        spyRoutingContext.setMethod(HttpMethod.POST);
        Client client = new Client();
        client.setFactors(Collections.singleton("factorId"));
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, "factorId");
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS, "user01@acme.fr");
        spyRoutingContext.session().put(ConstantKeys.MFA_CHALLENGE_SENT_FACTOR_ID_KEY, "factorId");
        spyRoutingContext.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(
                new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new User())));
        spyRoutingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        spyRoutingContext.put(ConstantKeys.TRANSACTION_ID_KEY, UUID.randomUUID().toString());
        spyRoutingContext.put(CONTEXT_PATH, "");

        mfaChallengeSendEndpoint.handle(spyRoutingContext);
        awaitResponseEnd(spyRoutingContext);

        verify(factorProvider, times(1)).sendChallenge(any());
        Assert.assertEquals("factorId", spyRoutingContext.session().get(ConstantKeys.MFA_CHALLENGE_SENT_FACTOR_ID_KEY));
        Assert.assertEquals(HttpStatusCode.OK_200, spyRoutingContext.response().getStatusCode());
        Assert.assertEquals(MediaType.APPLICATION_JSON, spyRoutingContext.response().headers().get("Content-Type"));
    }

    @Test
    public void shouldReturnJsonError_whenFactorDoesNotNeedChallengeSending() {
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(false);
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factorId");
        when(factorManager.get("factorId")).thenReturn(factorProvider);
        when(factorManager.getFactor("factorId")).thenReturn(factor);

        SpyRoutingContext spyRoutingContext = new SpyRoutingContext();
        spyRoutingContext.setMethod(HttpMethod.POST);
        Client client = new Client();
        client.setFactors(Collections.singleton("factorId"));
        spyRoutingContext.session().put(ConstantKeys.ENROLLED_FACTOR_ID_KEY, "factorId");
        spyRoutingContext.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(
                new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new User())));
        spyRoutingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

        mfaChallengeSendEndpoint.handle(spyRoutingContext);
        awaitResponseEnd(spyRoutingContext);

        Assert.assertEquals(HttpStatusCode.BAD_REQUEST_400, spyRoutingContext.response().getStatusCode());
        Assert.assertEquals(MediaType.APPLICATION_JSON, spyRoutingContext.response().headers().get("Content-Type"));
        verify(factorProvider, times(0)).sendChallenge(any());
    }
}
