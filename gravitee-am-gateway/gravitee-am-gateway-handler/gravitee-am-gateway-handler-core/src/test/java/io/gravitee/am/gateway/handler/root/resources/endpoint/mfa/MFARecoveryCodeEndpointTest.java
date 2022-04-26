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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.*;

import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.doAnswer;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MFARecoveryCodeEndpointTest extends RxWebTestBase {

    private static final String REQUEST_PATH = "/mfa/recovery_code";

    @Mock
    private Domain domain;

    @Mock
    private ThymeleafTemplateEngine templateEngine;

    @Mock
    private UserService userService;

    @Mock
    private FactorManager factorManager;

    @Mock
    private ApplicationContext applicationContext;

    private MFARecoveryCodeEndpoint mfaRecoveryCodeEndpoint;
    private Client client;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        client = new Client();
        client.setClientId(UUID.randomUUID().toString());
        final User user = new User();
        setFactorsFor(user);
        mfaRecoveryCodeEndpoint = new MFARecoveryCodeEndpoint(templateEngine, domain, userService, factorManager, applicationContext);

        router.route()
                .handler(ctx -> {
                    ctx.setUser(io.vertx.reactivex.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .handler(BodyHandler.create());
    }

    @Test
    public void shouldRenderWithRecoveryCodes() throws Exception {
        router.route(REQUEST_PATH)
                .handler(renderPageWithRecoveryCodes())
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.GET,
                REQUEST_PATH + "?client_id=" + client.getClientId() + "&redirect_uri=http://redirect.com/app",
                200,
                "OK");
    }

    private Handler<RoutingContext> renderPageWithRecoveryCodes() {
        return routingContext -> {
            doAnswer(answer -> {
                final List<String> recoveryCodes = routingContext.get("recoveryCodes");
                Assert.assertNotNull("context should have recovery codes", recoveryCodes);
                assertEquals("there should be 3 recovery codes", 3, recoveryCodes.size());
                final List<String> expected = Arrays.asList("one", "two", "three");
                assertThat(recoveryCodes, is(expected));

                routingContext.next();
                return answer;
            }).when(templateEngine).render(Mockito.<Map<String, Object>>any(), Mockito.any(), Mockito.any());

            mfaRecoveryCodeEndpoint.handle(routingContext);
        };
    }

    private void setFactorsFor(User user){
        final EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setSecurity(createFactorSecurityWithRecoveryCode());
        user.setFactors(Arrays.asList(new EnrolledFactor(), enrolledFactor));
    }

    private EnrolledFactorSecurity createFactorSecurityWithRecoveryCode(){
        return new EnrolledFactorSecurity(RECOVERY_CODE, "3",
                Map.of(RECOVERY_CODE, new ArrayList<>(Arrays.asList("one", "two", "three"))));
    }
}