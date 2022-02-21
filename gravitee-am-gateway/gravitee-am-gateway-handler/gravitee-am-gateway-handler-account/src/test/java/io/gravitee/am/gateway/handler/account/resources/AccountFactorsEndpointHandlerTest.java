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
package io.gravitee.am.gateway.handler.account.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AccountFactorsEndpointHandlerTest extends RxWebTestBase {

    private static final String REQUEST_PATH = "/account/api/";

    @Mock
    AccountService accountService;

    @Mock
    FactorManager factorManager;

    @Mock
    ApplicationContext applicationContext;

    private AccountFactorsEndpointHandler accountFactorsEndpointHandler;
    private User user;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        accountFactorsEndpointHandler = new AccountFactorsEndpointHandler(accountService, factorManager, applicationContext);
        user = new User();

        router.route()
                .handler(ctx -> {
                    ctx.put(ConstantKeys.USER_CONTEXT_KEY, user);
                    ctx.next();
                })
                .handler(BodyHandler.create())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void listEnrolledFactorsShouldNotReturnRecoveryCode() throws Exception {
        addFactors(user);

        router.route(REQUEST_PATH + "factors")
                .handler(accountFactorsEndpointHandler::listEnrolledFactors)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.GET,
                REQUEST_PATH + "factors",
                req -> req.headers().set("content-type", "application/json"),
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("Should contains 1 factor", 1, h.toJsonArray().size());
                        String body = h.toString();
                        assertTrue("SMS should present", body.contains("SMS"));
                        assertFalse("There should not be recovery code", body.contains("RECOVERY_CODE"));
                    });

                },
                200,
                "OK", null);
    }

    @Test
    public void listRecoveryCodesShouldReturnUserRecoveryCodes() throws Exception {
        addFactors(user);
        final String[] expectedRecoveryCodes = {"one", "two", "three"};

        router.route(REQUEST_PATH + "recovery_code")
                .handler(accountFactorsEndpointHandler::listRecoveryCodes)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.GET,
                REQUEST_PATH + "recovery_code",
                req -> req.headers().set("content-type", "application/json"),
                res -> {
                    res.bodyHandler(h -> {
                        ObjectMapper objectMapper = new ObjectMapper();
                        try {
                            final Object[] actualRecoveryCodes = objectMapper.readValue(h.toString(), List.class).toArray();
                            assertArrayEquals(expectedRecoveryCodes, actualRecoveryCodes);
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        }
                    });
                },
                200,
                "OK", null);
    }

    @Test
    public void shouldReturnEmptyListInAbsenceOfFactor() throws Exception {
        final String expectedValue = "[ ]";

        router.route(REQUEST_PATH + "recovery_code")
                .handler(accountFactorsEndpointHandler::listRecoveryCodes)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.GET,
                REQUEST_PATH + "recovery_code",
                req -> req.headers().set("content-type", "application/json"),
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("", expectedValue, h.toString());
                    });
                },
                200,
                "OK", null);
    }

    @Test
    public void shouldReturnEmptyListInAbsenceOfRecoveryCodeFactor() throws Exception {
        addSMSFactor(user);
        final String expectedValue = "[ ]";

        router.route(REQUEST_PATH + "recovery_code")
                .handler(accountFactorsEndpointHandler::listRecoveryCodes)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.GET,
                REQUEST_PATH + "recovery_code",
                req -> req.headers().set("content-type", "application/json"),
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("", expectedValue, h.toString());
                    });
                },
                200,
                "OK", null);
    }

    private void addFactors(User user) {
        final Map<String, Object> recoveryCode = Map.of(RECOVERY_CODE, Arrays.asList("one", "two", "three"));
        final EnrolledFactor securityEnrolledFactor = new EnrolledFactor();
        securityEnrolledFactor.setSecurity(new EnrolledFactorSecurity(RECOVERY_CODE, "3", recoveryCode));

        user.setFactors(Arrays.asList(securityEnrolledFactor, smsFactor()));
    }

    private void addSMSFactor(User user) {
        user.setFactors(List.of(smsFactor()));
    }

    private EnrolledFactor smsFactor() {
        final EnrolledFactor smsFactor = new EnrolledFactor();
        smsFactor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.SMS, "1234"));
        return smsFactor;
    }
}
