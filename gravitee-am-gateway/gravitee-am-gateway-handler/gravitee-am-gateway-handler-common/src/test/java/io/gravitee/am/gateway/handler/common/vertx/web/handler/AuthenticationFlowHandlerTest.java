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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.common.factor.FactorSecurityType;
import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.ruleengine.SpELRuleEngine;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSessionHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChainHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFAChallengeStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFAEnrollStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFARecoveryCodeStep;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.UserService;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static org.mockito.ArgumentMatchers.*;
import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import static io.gravitee.am.model.factor.FactorStatus.PENDING_ACTIVATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationFlowHandlerTest extends RxWebTestBase {

    @Mock
    private JWTService jwtService;
    @Mock
    private CertificateManager certificateManager;
    @Mock
    private UserService userService;
    @Mock
    private FactorManager factorManager;

    private SpELRuleEngine ruleEngine = new SpELRuleEngine();

    @Override
    public void setUp() throws Exception {
        super.setUp();

        List<AuthenticationFlowStep> steps = new LinkedList<>();
        steps.add(new MFAEnrollStep(RedirectHandler.create("/mfa/enroll"), ruleEngine, factorManager));
        steps.add(new MFAChallengeStep(RedirectHandler.create("/mfa/challenge"), ruleEngine, factorManager));
        steps.add(new MFARecoveryCodeStep(RedirectHandler.create("/mfa/recovery_code"), ruleEngine, factorManager));
        AuthenticationFlowChainHandler authenticationFlowChainHandler = new AuthenticationFlowChainHandler(steps);

        when(jwtService.encode(any(JWT.class), (CertificateProvider) eq(null))).thenReturn(Single.just("token"));

        Factor factor = new Factor();
        factor.setFactorType(FactorType.SMS);
        when(factorManager.getFactor(anyString())).thenReturn(factor);

        router.route("/login")
                .order(Integer.MIN_VALUE)
                .handler(new CookieSessionHandler(jwtService, certificateManager, userService, "am-cookie", 30 * 60 * 60));

        router.route("/login")
                .handler(authenticationFlowChainHandler)
                .handler(rc -> rc.response().setStatusCode(200).end())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldRedirectToMFAEnrollmentPage_nominalCase() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            rc.getDelegate().setUser(new User(endUser));
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/enroll"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAEnrollmentPage_adaptiveMFA() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setAdaptiveAuthenticationRule("{context.attributes['geoip']['country_iso_code'] == 'FR'");
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "US").getMap());
            // set user
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            rc.getDelegate().setUser(new User(endUser));
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/enroll"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAEnrollmentPage_adaptiveMFA_no_enroll() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setAdaptiveAuthenticationRule("{context.attributes['geoip']['country_iso_code'] == 'FR'");
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            // set user
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            rc.getDelegate().setUser(new User(endUser));
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/enroll"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAChallengePage_adaptiveMFA_no_active_enroll() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setAdaptiveAuthenticationRule("{context.attributes['geoip']['country_iso_code'] == 'FR'");
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(PENDING_ACTIVATION);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(List.of(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAEnrollmentPage_device_unknown() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
            client.setMfaSettings(mfaSettings);
            // set user
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            rc.getDelegate().setUser(new User(endUser));
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/enroll"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAChallengePage_nominalCase_no_enrolled_factor() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            rc.session().put(ENROLLED_FACTOR_ID_KEY, "factor-1");
            // set user
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            rc.getDelegate().setUser(new User(endUser));
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAChallengePage_nominalCase() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldContinue_user_strongly_auth() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login",
                HttpStatusCode.OK_200, "OK");
    }


    @Test
    public void shouldContinue_user_device_known() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user
            MFASettings mfaSettings = new MFASettings();
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldContinue_user_coming_from_mfa_challenge() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldRedirectToMFAChallengePage_stepUp_authentication() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setStepUpAuthenticationRule("{#request.params['scope'][0] == 'write'}");
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login?scope=write",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge?scope=write"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAChallengePage_adaptiveMFA() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setAdaptiveAuthenticationRule("{#context.attributes['geoip']['country_iso_code'] == 'FR'}");
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "US").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, false);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAChallengePage_adaptiveMFA_with_step_up_true_strong_auth_true() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
            mfaSettings.setStepUpAuthenticationRule("{#request.params['scope'][0].contains('write')}");
            mfaSettings.setAdaptiveAuthenticationRule("{#context.attributes['geoip']['country_iso_code'] == 'FR'}");
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login?scope=write",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge?scope=write"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldContinue_adaptiveMFA_with_step_up_false_strong_auth_true_device_known() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
            mfaSettings.setStepUpAuthenticationRule("{#request.params['scope'][0].contains('write')}");
            mfaSettings.setAdaptiveAuthenticationRule("{#context.attributes['geoip']['country_iso_code'] == 'FR'}");
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login?scope=read",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldRedirectToMFAChallengePage_rememberDevice() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAChallengePage_stepUp_authentication_2() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setStepUpAuthenticationRule("{#request.params['scope'][0].contains('write')}");
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login?scope=read%20write",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge?scope=read+write"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAChallengePage_adaptiveMFA_2() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setAdaptiveAuthenticationRule("{#context.attributes['geoip']['country_iso_code'] == 'FR'}");
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "US").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, false);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login?scope=read%20write",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge?scope=read+write"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAChallengePage_adaptiveMFA_2_strongly_auth() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setAdaptiveAuthenticationRule("{#context.attributes['geoip']['country_iso_code'] == 'FR'}");
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "US").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAChallengePage_adaptiveMFA_3_factor_is_pending() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setAdaptiveAuthenticationRule("{#context.attributes['geoip']['country_iso_code'] == 'FR'}");
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            client.setMfaSettings(mfaSettings);
            rc.session().put(ENROLLED_FACTOR_ID_KEY, "factor-1");

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(PENDING_ACTIVATION);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login?scope=read%20write",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge?scope=read+write"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAChallengePage_adaptiveMFA_3_alternate_factor_id() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setAdaptiveAuthenticationRule("{#context.attributes['geoip']['country_iso_code'] == 'FR'}");
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            client.setMfaSettings(mfaSettings);
            rc.session().put(ENROLLED_FACTOR_ID_KEY, "factor-1");
            rc.session().put(ALTERNATIVE_FACTOR_ID_KEY, "factor-2");
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);

            EnrolledFactor enrolledFactor2 = new EnrolledFactor();
            enrolledFactor2.setFactorId("factor-2");
            enrolledFactor2.setStatus(ACTIVATED);

            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(List.of(enrolledFactor, enrolledFactor2));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/challenge"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldContinue_stepUp_authentication_condition_not_met() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setStepUpAuthenticationRule("{#request.params['scope'][0] == 'write'}");
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login?scope=read",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldContinue_adaptiveMFA_condition_not_met() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            mfaSettings.setAdaptiveAuthenticationRule("{#context.attributes['geoip']['country_iso_code'] == 'FR'}");
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });


        testRequest(
                HttpMethod.GET, "/login",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldNotContinueUser_RecoveryCodeisAlreadyActive() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

            final Factor recoveryFactor = new Factor();
            recoveryFactor.setFactorType(FactorType.RECOVERY_CODE);
            recoveryFactor.setId("factor-2");
            when(factorManager.getFactor(eq("factor-2"))).thenReturn(recoveryFactor);

            client.setFactors(Set.of("factor-1", "factor-2"));
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            EnrolledFactor factorRecovery = new EnrolledFactor();
            factorRecovery.setFactorId("factor-2");

            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(List.of(enrolledFactor, factorRecovery));
            rc.getDelegate().setUser(new User(endUser));

            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/mfa/recovery_code"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldContinueUser_RecoveryCode_active() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

            client.setFactors(Set.of("factor-1", "factor-2"));

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);

            EnrolledFactor factorRecovery = new EnrolledFactor();
            factorRecovery.setFactorId("factor-2");
            factorRecovery.setStatus(ACTIVATED);
            factorRecovery.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.RECOVERY_CODE, null));

            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(List.of(enrolledFactor, factorRecovery));
            rc.getDelegate().setUser(new User(endUser));

            rc.session().put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                HttpStatusCode.OK_200, "OK");
    }
}
