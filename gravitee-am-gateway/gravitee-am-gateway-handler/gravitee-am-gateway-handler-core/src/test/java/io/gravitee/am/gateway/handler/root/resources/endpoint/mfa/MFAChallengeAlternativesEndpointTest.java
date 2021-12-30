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

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.client.ClientRequestParseHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static io.gravitee.am.common.utils.ConstantKeys.ACTION_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MFAChallengeAlternativesEndpointTest extends RxWebTestBase {

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private FactorManager factorManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        ClientRequestParseHandler clientRequestParseHandler = new ClientRequestParseHandler(clientSyncService);
        clientRequestParseHandler.setRequired(true);

        domain = new Domain();
        domain.setId(UUID.randomUUID().toString());
        domain.setPath("/");

        when(clientSyncService.findByClientId(anyString())).thenReturn(Maybe.just(new Client()));

        router.route(HttpMethod.GET, "/mfa/challenge/alternatives")
                .handler(clientRequestParseHandler)
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void mustNotInvokeMFAChallengeAlternativesEndpoint_noClientId() throws Exception {
        testRequest(
                HttpMethod.GET, "/mfa/challenge/alternatives",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void mustNotInvokeMFAChallengeAlternativesEndpoint_noUser() throws Exception {
        router.route(HttpMethod.GET, "/mfa/challenge/alternatives")
                .handler(new MFAChallengeAlternativesEndpoint(templateEngine, factorManager));

        testRequest(
                HttpMethod.GET, "/mfa/challenge/alternatives?client_id=test",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void mustNotInvokeMFAChallengeAlternativesEndpoint_user_noFactor() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        router.route(HttpMethod.GET, "/mfa/challenge/alternatives")
                .handler(new MFAChallengeAlternativesEndpoint(templateEngine, factorManager));

        testRequest(
                HttpMethod.GET, "/mfa/challenge/alternatives?client_id=test",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void mustNotInvokeMFAChallengeAlternativesEndpoint_user_oneFactor() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setFactors(Collections.singletonList(new EnrolledFactor()));
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        router.route(HttpMethod.GET, "/mfa/challenge/alternatives")
                .handler(new MFAChallengeAlternativesEndpoint(templateEngine, factorManager));

        testRequest(
                HttpMethod.GET, "/mfa/challenge/alternatives?client_id=test",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void mustInvokeMFAChallengeAlternativesEndpoint() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            EnrolledFactor enrolledFactor1 = new EnrolledFactor();
            enrolledFactor1.setFactorId("factor1");
            EnrolledFactor enrolledFactor2 = new EnrolledFactor();
            enrolledFactor2.setFactorId("factor2");

            User endUser = new User();
            endUser.setFactors(Arrays.asList(enrolledFactor1, enrolledFactor2));
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        router.route(HttpMethod.GET, "/mfa/challenge/alternatives")
                .handler(get200AssertMockRoutingContextHandler(new MFAChallengeAlternativesEndpoint(templateEngine, factorManager)));

        testRequest(
                HttpMethod.GET, "/mfa/challenge/alternatives?client_id=test",
                HttpStatusCode.OK_200, "OK");
    }

    private Handler<RoutingContext> get200AssertMockRoutingContextHandler(Handler handler) {
        return routingContext -> {
            doAnswer(answer -> {
                try {
                    assertNotNull(routingContext.get(CLIENT_CONTEXT_KEY));
                    final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
                    assertEquals(routingContext.get(ACTION_KEY), resolveProxyRequest(routingContext.request(),"/mfa/challenge/alternatives", queryParams, true));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    routingContext.end();
                }
                return answer;
            }).when(templateEngine).render(Mockito.<Map<String, Object>>any(), Mockito.any(), Mockito.any());
            handler.handle(routingContext);
        };
    }
}
