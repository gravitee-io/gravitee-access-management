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
import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.common.exception.mfa.SendChallengeException;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.account.resources.util.AccountRoutes;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.factor.FactorSecurityType.RECOVERY_CODE;
import static io.gravitee.am.common.factor.FactorSecurityType.SHARED_SECRET;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
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

    @Mock
    RateLimiterService rateLimiterService;

    @Mock
    AuditService auditService;

    private AccountFactorsEndpointHandler accountFactorsEndpointHandler;
    private User user;
    private Client client;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        accountFactorsEndpointHandler = new AccountFactorsEndpointHandler(accountService, factorManager, applicationContext, rateLimiterService, auditService);
        user = new User();
        user.setId("xxx-xxx-xxx");
        user.setUsername("username");
        user.setClient("clientId");
        client = new Client();
        client.setId("clientId");
        client.setDomain("domainId");


        router.route()
                .handler(BodyHandler.create())
                .handler(ctx -> {
                    ctx.put(ConstantKeys.USER_CONTEXT_KEY, user);
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
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

    @Test
    public void shouldNotVerifyFactor_invalidRequest() throws Exception {
        router.post(AccountRoutes.FACTORS_VERIFY.getRoute())
                .handler(accountFactorsEndpointHandler::verifyFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/verify",
                null,
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"message\" : \"Unable to parse body message\",\n" +
                                "  \"http_status\" : 400\n" +
                                "}", h.toString());
                    });
                },
                400,
                "Bad Request", null);
    }

    @Test
    public void shouldNotVerifyFactor_missingCode() throws Exception {
        router.post(AccountRoutes.FACTORS_VERIFY.getRoute())
                .handler(accountFactorsEndpointHandler::verifyFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/verify",
                req -> {

                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{\"invalidCodeKey\":\"123456\"}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"message\" : \"Field [code] is required\",\n" +
                                "  \"http_status\" : 400\n" +
                                "}", h.toString());
                    });
                },
                400,
                "Bad Request", null);
    }

    @Test
    public void shouldNotVerifyFactor_unknownFactor() throws Exception {
        router.post(AccountRoutes.FACTORS_VERIFY.getRoute())
                .handler(accountFactorsEndpointHandler::verifyFactor)
                .handler(rc -> rc.response().end());

        when(accountService.getFactor("factor-id")).thenReturn(Maybe.empty());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/verify",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{\"code\":\"123456\"}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"message\" : \"Factor [factor-id] can not be found.\",\n" +
                                "  \"http_status\" : 404\n" +
                                "}", h.toString());
                    });
                },
                404,
                "Not Found", null);
    }

    @Test
    public void shouldNotVerifyFactor_invalidFactor() throws Exception {
        router.post(AccountRoutes.FACTORS_VERIFY.getRoute())
                .handler(accountFactorsEndpointHandler::verifyFactor)
                .handler(rc -> rc.response().end());

        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(new Factor()));
        when(factorManager.get("factor-id")).thenReturn(null);

        testRequest(HttpMethod.POST, "/api/factors/factor-id/verify",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{\"code\":\"123456\"}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"message\" : \"Factor [factor-id] can not be found.\",\n" +
                                "  \"http_status\" : 404\n" +
                                "}", h.toString());
                    });
                },
                404,
                "Not Found", null);
    }

    @Test
    public void shouldNotVerifyFactor_factorNotEnrolled() throws Exception {
        Factor factor = mock(Factor.class);
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);

        router.post(AccountRoutes.FACTORS_VERIFY.getRoute())
                .handler(rc -> {
                    User user = rc.get(ConstantKeys.USER_CONTEXT_KEY);
                    user.setFactors(Collections.emptyList());
                    rc.next();
                })
                .handler(accountFactorsEndpointHandler::verifyFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/verify",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{\"code\":\"123456\"}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"message\" : \"Factor [factor-id] can not be found.\",\n" +
                                "  \"http_status\" : 404\n" +
                                "}", h.toString());
                    });
                },
                404,
                "Not Found", null);
    }

    @Test
    public void shouldNotVerifyFactor_invalidCode() throws Exception {
        Factor factor = mock(Factor.class);
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.verify(any())).thenReturn(Completable.error(new InvalidCodeException("invalid code")));
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);

        router.post(AccountRoutes.FACTORS_VERIFY.getRoute())
                .handler(rc -> {
                    User user = rc.get(ConstantKeys.USER_CONTEXT_KEY);
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    enrolledFactor.setFactorId("factor-id");
                    user.setFactors(Collections.singletonList(enrolledFactor));
                    rc.next();
                })
                .handler(accountFactorsEndpointHandler::verifyFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/verify",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{\"code\":\"123456\"}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"message\" : \"invalid_mfa : invalid code\",\n" +
                                "  \"http_status\" : 403\n" +
                                "}", h.toString());
                    });
                },
                403,
                "Forbidden", null);

        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldVerifyFactor_nominalCase() throws Exception {
        Factor factor = mock(Factor.class);
        FactorProvider factorProvider = mock(FactorProvider.class);
        EnrolledFactor enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("factor-id");
        when(factorProvider.verify(any())).thenReturn(Completable.complete());
        when(factorProvider.changeVariableFactorSecurity(any())).thenReturn(Single.just(enrolledFactor));
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);
        when(accountService.upsertFactor(any(), any(), any())).thenReturn(Single.just(new User()));

        router.post(AccountRoutes.FACTORS_VERIFY.getRoute())
                .handler(rc -> {
                    User user = rc.get(ConstantKeys.USER_CONTEXT_KEY);
                    user.setFactors(Collections.singletonList(enrolledFactor));
                    rc.next();
                })
                .handler(accountFactorsEndpointHandler::verifyFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/verify",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{\"code\":\"123456\"}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"factorId\" : \"factor-id\",\n" +
                                "  \"appId\" : null,\n" +
                                "  \"status\" : \"ACTIVATED\",\n" +
                                "  \"security\" : null,\n" +
                                "  \"channel\" : null,\n" +
                                "  \"primary\" : null,\n" +
                                "  \"createdAt\" : null,\n" +
                                "  \"updatedAt\" : null\n" +
                                "}", h.toString());
                    });
                },
                200,
                "OK", null);

        verify(factorProvider, times(1)).changeVariableFactorSecurity(any());
        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldNotSendChallenge_unknownFactor() throws Exception {
        router.post(AccountRoutes.FACTORS_SEND_CHALLENGE.getRoute())
                .handler(accountFactorsEndpointHandler::sendChallenge)
                .handler(rc -> rc.response().end());

        when(accountService.getFactor("factor-id")).thenReturn(Maybe.empty());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/sendChallenge",
                null,
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"message\" : \"Factor [factor-id] can not be found.\",\n" +
                                "  \"http_status\" : 404\n" +
                                "}", h.toString());
                    });
                },
                404,
                "Not Found", null);
    }

    @Test
    public void shouldNotSendChallenge_invalidFactor() throws Exception {
        router.post(AccountRoutes.FACTORS_SEND_CHALLENGE.getRoute())
                .handler(accountFactorsEndpointHandler::sendChallenge)
                .handler(rc -> rc.response().end());

        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(new Factor()));
        when(factorManager.get("factor-id")).thenReturn(null);

        testRequest(HttpMethod.POST, "/api/factors/factor-id/sendChallenge",
                null,
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"message\" : \"Factor [factor-id] can not be found.\",\n" +
                                "  \"http_status\" : 404\n" +
                                "}", h.toString());
                    });
                },
                404,
                "Not Found", null);
    }

    @Test
    public void shouldNotSendChallenge_factorNotEnrolled() throws Exception {
        Factor factor = mock(Factor.class);
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);

        router.post(AccountRoutes.FACTORS_SEND_CHALLENGE.getRoute())
                .handler(rc -> {
                    User user = rc.get(ConstantKeys.USER_CONTEXT_KEY);
                    user.setFactors(Collections.emptyList());
                    rc.next();
                })
                .handler(accountFactorsEndpointHandler::sendChallenge)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/sendChallenge",
                null,
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"message\" : \"Factor [factor-id] can not be found.\",\n" +
                                "  \"http_status\" : 404\n" +
                                "}", h.toString());
                    });
                },
                404,
                "Not Found", null);
    }

    @Test
    public void shouldNotSendChallenge_sendChallengeException() throws Exception {
        Factor factor = mock(Factor.class);
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(factorProvider.sendChallenge(any())).thenReturn(Completable.error(new SendChallengeException("unable to send the challenge")));
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);
        when(factorManager.getFactor("factor-id")).thenReturn(factor);

        router.post(AccountRoutes.FACTORS_SEND_CHALLENGE.getRoute())
                .handler(rc -> {
                    User user = rc.get(ConstantKeys.USER_CONTEXT_KEY);
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    enrolledFactor.setFactorId("factor-id");
                    user.setFactors(Collections.singletonList(enrolledFactor));
                    rc.next();
                })
                .handler(accountFactorsEndpointHandler::sendChallenge)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/sendChallenge",
                null,
                null,
                500,
                "Internal Server Error", null);
    }

    @Test
    public void shouldNotSendChallenge_noNeedChallenge() throws Exception {
        Factor factor = mock(Factor.class);
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(false);
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);

        router.post(AccountRoutes.FACTORS_SEND_CHALLENGE.getRoute())
                .handler(rc -> {
                    User user = rc.get(ConstantKeys.USER_CONTEXT_KEY);
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    enrolledFactor.setFactorId("factor-id");
                    user.setFactors(Collections.singletonList(enrolledFactor));
                    rc.next();
                })
                .handler(accountFactorsEndpointHandler::sendChallenge)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/sendChallenge",
                null,
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"message\" : \"Invalid factor\",\n" +
                                "  \"http_status\" : 400\n" +
                                "}", h.toString());
                    });
                },
                400,
                "Bad Request", null);

        verify(factorProvider, never()).sendChallenge(any());
    }

    @Test
    public void shouldSendChallenge_nominalCase() throws Exception {
        Factor factor = mock(Factor.class);
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(factorProvider.sendChallenge(any())).thenReturn(Completable.complete());
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);
        when(factorManager.getFactor("factor-id")).thenReturn(factor);
        when(factor.getId()).thenReturn("any-factor-id");

        router.post(AccountRoutes.FACTORS_SEND_CHALLENGE.getRoute())
                .handler(rc -> {
                    User user = rc.get(ConstantKeys.USER_CONTEXT_KEY);
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    enrolledFactor.setFactorId("factor-id");
                    user.setFactors(Collections.singletonList(enrolledFactor));
                    rc.next();
                })
                .handler(accountFactorsEndpointHandler::sendChallenge)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/sendChallenge",
                null,
                res -> {
                    res.bodyHandler(h -> {
                        assertEquals("{\n" +
                                "  \"factorId\" : \"factor-id\",\n" +
                                "  \"appId\" : null,\n" +
                                "  \"status\" : \"NULL\",\n" +
                                "  \"security\" : null,\n" +
                                "  \"channel\" : null,\n" +
                                "  \"primary\" : null,\n" +
                                "  \"createdAt\" : null,\n" +
                                "  \"updatedAt\" : null\n" +
                                "}", h.toString());
                    });
                },
                200,
                "OK", null);

        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldNotEnrollFactor_invalidRequest_noJson() throws Exception {
        router.post(AccountRoutes.FACTORS.getRoute())
                .handler(accountFactorsEndpointHandler::enrollFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("wrong-body");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                null,
                400,
                "Bad Request", null);
    }

    @Test
    public void shouldNotEnrollFactor_invalidRequest_nullPayload() throws Exception {
        router.post(AccountRoutes.FACTORS.getRoute())
                .handler(accountFactorsEndpointHandler::enrollFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors",
               null,
                null,
                400,
                "Bad Request", null);
    }
    @Test
    public void shouldNotEnrollFactor_invalidRequest_missingFactorId() throws Exception {
        router.post(AccountRoutes.FACTORS.getRoute())
                .handler(accountFactorsEndpointHandler::enrollFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                null,
                400,
                "Bad Request", null);
    }

    @Test
    public void shouldNotEnrollFactor_invalidRequest_factorNotFound() throws Exception {
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.empty());

        router.post(AccountRoutes.FACTORS.getRoute())
                .handler(accountFactorsEndpointHandler::enrollFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{\n" +
                            "    \"factorId\": \"factor-id\",\n" +
                            "    \"account\": {\n" +
                            "        \"email\": \"mail@mail.com\"\n" +
                            "    }\n" +
                            "}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                null,
                404,
                "Not Found", null);
    }

    @Test
    public void shouldNotEnrollFactor_invalidRequest_factorProviderNotFound() throws Exception {
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(new Factor()));

        router.post(AccountRoutes.FACTORS.getRoute())
                .handler(accountFactorsEndpointHandler::enrollFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{\n" +
                            "    \"factorId\": \"factor-id\",\n" +
                            "    \"account\": {\n" +
                            "        \"email\": \"mail@mail.com\"\n" +
                            "    }\n" +
                            "}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                null,
                404,
                "Not Found", null);
    }

    @Test
    public void shouldEnrollFactor_nominalCase() throws Exception {
        Enrollment enrollment = mock(Enrollment.class);
        when(enrollment.getKey()).thenReturn(SHARED_SECRET);
        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.enroll(any(FactorContext.class))).thenReturn(Single.just(enrollment));
        when(factorProvider.checkSecurityFactor(any())).thenReturn(true);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(factorProvider.useVariableFactorSecurity(any())).thenReturn(true);
        when(factorProvider.sendChallenge(any())).thenReturn(Completable.complete());
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        ArgumentCaptor<EnrolledFactor> enrolledFactorCaptor = ArgumentCaptor.forClass(EnrolledFactor.class);
        when(accountService.upsertFactor(any(), enrolledFactorCaptor.capture(), any())).thenReturn(Single.just(new User()));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);
        when(factorManager.getFactor("any-factor-id")).thenReturn(factor);
        when(factor.getId()).thenReturn("any-factor-id");

        router.post(AccountRoutes.FACTORS.getRoute())
                .handler(accountFactorsEndpointHandler::enrollFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{\n" +
                            "    \"factorId\": \"factor-id\",\n" +
                            "    \"account\": {\n" +
                            "        \"email\": \"mail@mail.com\"\n" +
                            "    }\n" +
                            "}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                null,
                200,
                "OK", null);

        EnrolledFactor enrolledFactorCaptorValue = enrolledFactorCaptor.getValue();
        Assert.assertNotNull(enrolledFactorCaptorValue);
        Assert.assertNull(enrolledFactorCaptorValue.getSecurity());
        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldEnrollFactor_withPhoneExtension() throws Exception {
        Enrollment enrollment = mock(Enrollment.class);
        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.SMS);
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.enroll(any(FactorContext.class))).thenReturn(Single.just(enrollment));
        when(factorProvider.checkSecurityFactor(any())).thenReturn(true);
        when(factorProvider.needChallengeSending()).thenReturn(false);
        when(factorProvider.useVariableFactorSecurity(any())).thenReturn(false);
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        ArgumentCaptor<EnrolledFactor> enrolledFactorCaptor = ArgumentCaptor.forClass(EnrolledFactor.class);
        when(accountService.upsertFactor(any(), enrolledFactorCaptor.capture(), any())).thenReturn(Single.just(new User()));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);

        router.post(AccountRoutes.FACTORS.getRoute())
                .handler(accountFactorsEndpointHandler::enrollFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{\n" +
                            "    \"factorId\": \"factor-id\",\n" +
                            "    \"account\": {\n" +
                            "        \"phoneNumber\": \"+33611111111\",\n" +
                            "        \"extensionPhoneNumber\": \"1234\"\n" +
                            "    }\n" +
                            "}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                null,
                200,
                "OK", null);

        EnrolledFactor enrolledFactorCaptorValue = enrolledFactorCaptor.getValue();
        Assert.assertNotNull(enrolledFactorCaptorValue);
        Assert.assertEquals(EnrolledFactorChannel.Type.SMS, enrolledFactorCaptorValue.getChannel().getType());
        Assert.assertEquals("+33611111111", enrolledFactorCaptorValue.getChannel().getTarget());
        Assert.assertEquals("1234", enrolledFactorCaptorValue.getChannel().getAdditionalData().get(ConstantKeys.MFA_ENROLLMENT_EXTENSION_PHONE_NUMBER));
    }

    @Test
    public void shouldAuditLog_rateLimit() throws Exception {
        Factor factor = mock(Factor.class);
        Client client = mock(Client.class);

        final String domainId = "any-domain-id";
        final String clientId = "any-client-id";
        final String factorId = "any-factor-id";

        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);
        when(factorManager.getFactor("factor-id")).thenReturn(factor);
        when(factor.getId()).thenReturn(factorId);
        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(client.getDomain()).thenReturn(domainId);
        when(client.getId()).thenReturn(clientId);

        when(rateLimiterService.tryConsume("xxx-xxx-xxx", factorId, clientId, domainId))
                .thenReturn(Single.just(Boolean.FALSE));

        router.post(AccountRoutes.FACTORS_SEND_CHALLENGE.getRoute())
                .handler(rc -> {
                    User user = rc.get(ConstantKeys.USER_CONTEXT_KEY);
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    enrolledFactor.setFactorId("factor-id");
                    user.setFactors(Collections.singletonList(enrolledFactor));
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                })
                .handler(accountFactorsEndpointHandler::sendChallenge)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/sendChallenge", null,
                429, "Too Many Requests", null);

        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldReturnSharedSecret() throws Exception {
        final EnrolledFactor securityEnrolledFactor = new EnrolledFactor();
        securityEnrolledFactor.setFactorId("factor-id");
        securityEnrolledFactor.setSecurity(new EnrolledFactorSecurity(SHARED_SECRET, "1234"));
        user.setFactors(List.of(securityEnrolledFactor));

        router.route(AccountRoutes.FACTORS_OTP_SHARED_SECRET.getRoute())
                .handler(accountFactorsEndpointHandler::getEnrolledFactorSharedSecretCode)
                .handler(rc -> rc.response().end());



        testRequest(HttpMethod.GET, "/api/factors/factor-id/sharedSecret",
                null,
                res -> res.bodyHandler(h -> {
                    assertEquals("{\n" +
                            "  \"sharedSecret\" : \"1234\"\n" +
                            "}", h.toString());
                }),
                200,
                "OK", null);
    }

    @Test
    public void shouldReturnRateLimitExceptionOnSendChallenge() throws Exception {
        Factor factor = mock(Factor.class);
        when(factor.getId()).thenReturn("factor-id");
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);
        when(factorManager.getFactor(anyString())).thenReturn(factor);
        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(rateLimiterService.tryConsume(anyString(), anyString(), anyString(), anyString())).thenReturn(Single.just(Boolean.FALSE));

        router.post(AccountRoutes.FACTORS_SEND_CHALLENGE.getRoute())
                .handler(rc -> {
                    User user = rc.get(ConstantKeys.USER_CONTEXT_KEY);
                    EnrolledFactor enrolledFactor = new EnrolledFactor();
                    enrolledFactor.setFactorId("factor-id");
                    user.setFactors(Collections.singletonList(enrolledFactor));
                    rc.next();
                })
                .handler(accountFactorsEndpointHandler::sendChallenge)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors/factor-id/sendChallenge",
                null,
                res -> res.bodyHandler(h -> assertEquals("{\n" +
                        "  \"message\" : \"MFA rate limit reached\",\n" +
                        "  \"http_status\" : 429\n" +
                        "}", h.toString())),
                429,
                "Too Many Requests", null);


    }

    @Test
    public void shouldReturnRateLimitExceptionOnEnrollFactor() throws Exception {
        Enrollment enrollment = mock(Enrollment.class);
        when(enrollment.getKey()).thenReturn(SHARED_SECRET);
        Factor factor = mock(Factor.class);
        when(factor.getFactorType()).thenReturn(FactorType.EMAIL);
        when(factor.getId()).thenReturn("factor-id");
        FactorProvider factorProvider = mock(FactorProvider.class);
        when(factorProvider.enroll(any(FactorContext.class))).thenReturn(Single.just(enrollment));
        when(factorProvider.checkSecurityFactor(any())).thenReturn(true);
        when(factorProvider.needChallengeSending()).thenReturn(true);
        when(factorProvider.useVariableFactorSecurity(any())).thenReturn(true);
        when(accountService.getFactor("factor-id")).thenReturn(Maybe.just(factor));
        when(factorManager.get("factor-id")).thenReturn(factorProvider);
        when(factorManager.getFactor(anyString())).thenReturn(factor);
        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(rateLimiterService.tryConsume(anyString(), anyString(), anyString(), anyString())).thenReturn(Single.just(Boolean.FALSE));

        router.post(AccountRoutes.FACTORS.getRoute())
                .handler(accountFactorsEndpointHandler::enrollFactor)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST, "/api/factors",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("{\n" +
                            "    \"factorId\": \"factor-id\",\n" +
                            "    \"account\": {\n" +
                            "        \"email\": \"mail@mail.com\"\n" +
                            "    }\n" +
                            "}");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                res -> res.bodyHandler(h -> assertEquals("{\n" +
                        "  \"message\" : \"MFA rate limit reached\",\n" +
                        "  \"http_status\" : 429\n" +
                        "}", h.toString())),
                429,
                "Too Many Requests", null);
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
