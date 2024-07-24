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
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.ruleengine.SpELRuleEngine;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSessionHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowChainHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.AuthenticationFlowStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFAChallengeStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFAEnrollStep;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MFARecoveryCodeStep;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.model.MfaEnrollType;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.StepUpAuthenticationSettings;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.impl.user.UserEnhancer;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.risk.assessment.api.assessment.Assessment;
import io.gravitee.risk.assessment.api.assessment.AssessmentMessageResult;
import io.gravitee.risk.assessment.api.assessment.AssessmentResult;
import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.ALTERNATIVE_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.RISK_ASSESSMENT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.SILENT_AUTH_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.STRONG_AUTH_COMPLETED_KEY;
import static io.gravitee.am.model.factor.FactorStatus.ACTIVATED;
import static io.gravitee.am.model.factor.FactorStatus.PENDING_ACTIVATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationFlowHandlerTest extends RxWebTestBase {


    private static final String DEFAULT_FACTOR_ID = "default-factor";
    private static final String FACTOR_RECOVERY_CODE_ID = "factor-recovery-code";
    private static final String FACTOR_ID = "factor-id";
    private final SpELRuleEngine ruleEngine = new SpELRuleEngine();
    @Mock
    private JWTService jwtService;
    @Mock
    private CertificateManager certificateManager;
    @Mock
    private UserEnhancer userEnhancer;
    @Mock
    private FactorManager factorManager;
    @Mock
    private SubjectManager subjectManager;

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

        Factor recoveryCodeFactor = new Factor();
        recoveryCodeFactor.setFactorType(FactorType.RECOVERY_CODE);

        when(factorManager.getFactor(anyString())).thenReturn(factor);
        when(factorManager.getFactor("factor-recovery-code")).thenReturn(recoveryCodeFactor);

        router.route("/login")
                .order(Integer.MIN_VALUE)
                .handler(new CookieSessionHandler(jwtService, certificateManager, subjectManager, userEnhancer, "am-cookie", 30 * 60 * 60));

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
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(false, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            client.setMfaSettings(mfaSettings);
            setFactors(client);

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
            String rule = "{context.attributes['geoip']['country_iso_code'] == 'FR'";
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.CONDITIONAL, rule);
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            client.setMfaSettings(mfaSettings);
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
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
            String rule = "{context.attributes['geoip']['country_iso_code'] == 'FR'";
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.CONDITIONAL, rule);
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            client.setMfaSettings(mfaSettings);
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
    public void shouldNoRedirectToMFAChallengePage_adaptiveMFA_no_active_enroll_and_endUser_not_enrolled() throws Exception {
        router.route().order(-1).handler(rc -> {
            String rule = "{context.attributes['geoip']['country_iso_code'] == 'FR'";
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.CONDITIONAL, rule);
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            client.setMfaSettings(mfaSettings);
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
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
                    assertTrue(location.endsWith("/mfa/enroll"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToMFAChallengePage_adaptiveMFA_no_active_enroll_and_endUser_is_enrolling() throws Exception {
        router.route().order(-1).handler(rc -> {
            String rule = "{context.attributes['geoip']['country_iso_code'] == 'FR'";
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.CONDITIONAL, rule);
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            client.setMfaSettings(mfaSettings);
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(PENDING_ACTIVATION);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(List.of(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ENROLLED_FACTOR_ID_KEY, FACTOR_ID);
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
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            EnrollSettings enrollSettings = createEnrollSettings(false, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
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
    public void shouldSkipEnrollmentPage_silent_auth() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

            MFASettings mfaSettings = new MFASettings();
            client.setMfaSettings(mfaSettings);

            // set user
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            rc.getDelegate().setUser(new User(endUser));

            // silent auth
            rc.put(SILENT_AUTH_CONTEXT_KEY, true);

            rc.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldRedirectToMFAChallengePage_nominalCase_no_enrolled_factor() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            EnrollSettings enrollSettings = createEnrollSettings(false, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            Client client = new Client();
            setFactors(client);
            client.setMfaSettings(mfaSettings);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            rc.session().put(ENROLLED_FACTOR_ID_KEY, FACTOR_ID);
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
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            client.setMfaSettings(mfaSettings);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
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
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
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
            setFactors(client);
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
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });


        testRequest(
                HttpMethod.GET,
                "/login",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldNoRedirectToMFAChallengePage_device_remembered_but_no_matching_active_factor_and_endUser_not_enrolled() throws Exception {
        router.route().order(-1).handler(rc -> {
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(PENDING_ACTIVATION);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
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
    public void shouldRedirectToMFAChallengePage_device_remembered_but_no_matching_active_factor_and_endUser_is_enrolling() throws Exception {
        router.route().order(-1).handler(rc -> {
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(PENDING_ACTIVATION);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
            rc.session().put(ENROLLED_FACTOR_ID_KEY, FACTOR_ID);
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
    public void shouldNoRedirectToMFAChallengePage_device_remembered_but_active_factor_is_recovery_code_and_endUser_not_enrolled() throws Exception {
        router.route().order(-1).handler(rc -> {
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactorsWithRecoveryCode(client);

            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
            client.setMfaSettings(mfaSettings);

            // set user
            EnrolledFactor enrolledRecovery = new EnrolledFactor();
            enrolledRecovery.setFactorId(FACTOR_RECOVERY_CODE_ID);
            enrolledRecovery.setStatus(ACTIVATED);

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(PENDING_ACTIVATION);

            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(List.of(enrolledRecovery, enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, false);
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
    public void shouldRedirectToMFAChallengePage_device_remembered_but_active_factor_is_recovery_code_and_endUser_is_enrolling() throws Exception {
        router.route().order(-1).handler(rc -> {
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactorsWithRecoveryCode(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user

            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
            client.setMfaSettings(mfaSettings);

            // set user
            EnrolledFactor enrolledRecovery = new EnrolledFactor();
            enrolledRecovery.setFactorId("factor-recovery-code");
            enrolledRecovery.setStatus(ACTIVATED);

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(PENDING_ACTIVATION);

            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(List.of(enrolledRecovery, enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, false);
            rc.session().put(ENROLLED_FACTOR_ID_KEY, "factor-id");
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
    public void shouldRedirectToChallenge_user_device_known_and_step_up_active_strongly_auth() throws Exception {
        router.route().order(-1).handler(rc -> {
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user

            var stepUpAuthenticationRule = "{true}";
            var stepUpAuthentication = new StepUpAuthenticationSettings();
            stepUpAuthentication.setActive(true);
            stepUpAuthentication.setStepUpAuthenticationRule(stepUpAuthenticationRule);
            mfaSettings.setStepUpAuthentication(stepUpAuthentication);
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
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
    public void shouldRedirectToChallenge_user_device_known_and_step_up_active_not_strongly_auth() throws Exception {
        router.route().order(-1).handler(rc -> {
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

            var stepUpAuthenticationRule = "{true}";
            var stepUpAuthentication = new StepUpAuthenticationSettings();
            stepUpAuthentication.setActive(true);
            stepUpAuthentication.setStepUpAuthenticationRule(stepUpAuthenticationRule);
            mfaSettings.setStepUpAuthentication(stepUpAuthentication);

            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, false);
            rc.session().put(MFA_CHALLENGE_COMPLETED_KEY, false);
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
    public void shouldContinue_user_device_known_and_step_up_active_but_not_match() throws Exception {
        router.route().order(-1).handler(rc -> {
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

            var stepUpAuthenticationRule = "{false}";
            var stepUpAuthentication = new StepUpAuthenticationSettings();
            stepUpAuthentication.setActive(true);
            stepUpAuthentication.setStepUpAuthenticationRule(stepUpAuthenticationRule);
            mfaSettings.setStepUpAuthentication(stepUpAuthentication);

            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
            client.setMfaSettings(mfaSettings);
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, false);
            rc.session().put(MFA_CHALLENGE_COMPLETED_KEY, false);
            rc.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldContinue_user_device_known_and_step_up_active_strongly_auth() throws Exception {
        router.route().order(-1).handler(rc -> {
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

            var stepUpAuthenticationRule = "{true}";
            var stepUpAuthentication = new StepUpAuthenticationSettings();
            stepUpAuthentication.setActive(true);
            stepUpAuthentication.setStepUpAuthenticationRule(stepUpAuthenticationRule);
            mfaSettings.setStepUpAuthentication(stepUpAuthentication);

            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
            rc.session().put(MFA_CHALLENGE_COMPLETED_KEY, true);
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
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
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
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

            var stepUpAuthenticationRule = "{#request.params['scope'][0] == 'write'}";
            var stepUpAuthentication = new StepUpAuthenticationSettings();
            stepUpAuthentication.setActive(true);
            stepUpAuthentication.setStepUpAuthenticationRule(stepUpAuthenticationRule);

            mfaSettings.setStepUpAuthentication(stepUpAuthentication);
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
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
            String challengeRule = "{#context.attributes['geoip']['country_iso_code'] == 'FR'}";
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.CONDITIONAL, challengeRule);
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "US").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, false);
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
            String challengeRule = "{#context.attributes['geoip']['country_iso_code'] == 'FR'}";
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.RISK_BASED, challengeRule);
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);

            var stepUpAuthenticationRule = "{#request.params['scope'][0].contains('write')}";
            var stepUpAuthentication = new StepUpAuthenticationSettings();
            stepUpAuthentication.setActive(true);
            stepUpAuthentication.setStepUpAuthenticationRule(stepUpAuthenticationRule);
            mfaSettings.setStepUpAuthentication(stepUpAuthentication);

            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login?scope=read",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldContinue_adaptiveMFA_with_step_up_false_strong_auth_true_device_known() throws Exception {
        router.route().order(-1).handler(rc -> {
            String rule = "{#context.attributes['geoip']['country_iso_code'] == 'FR'}";
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.RISK_BASED, rule);
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, true);

            var stepUpAuthenticationRule = "{#request.params['scope'][0].contains('write')}";
            var stepUpAuthentication = new StepUpAuthenticationSettings();
            stepUpAuthentication.setActive(true);
            stepUpAuthentication.setStepUpAuthenticationRule(stepUpAuthenticationRule);
            mfaSettings.setStepUpAuthentication(stepUpAuthentication);

            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
            rc.session().put(MFA_CHALLENGE_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login?scope=read",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldRedirectToMFAChallengePage_rememberDevice() throws Exception {
        router.route().order(-1).handler(rc -> {
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
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
    public void shouldSkipMFAChallengePage_silent_auth() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            client.setFactors(Collections.singleton("factor-1"));

            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            client.setMfaSettings(mfaSettings);

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId("factor-1");
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));

            // silent auth
            rc.put(SILENT_AUTH_CONTEXT_KEY, true);

            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldContinue_rememberDevice_with_device_assessment_enabled() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            client.setRiskAssessment(
                    new RiskAssessmentSettings().setEnabled(true).setDeviceAssessment(
                            new AssessmentSettings().setEnabled(true)
                    )
            );
            MFASettings mfaSettings = new MFASettings();
            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(RISK_ASSESSMENT_KEY, new AssessmentMessageResult().setDevices(
                    new AssessmentResult<Double>().setAssessment(Assessment.SAFE).setResult(0D)
            ));
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldRedirectToMFAChallengePage_rememberDevice_with_risk_assessment_but_no_device() throws Exception {
        router.route().order(-1).handler(rc -> {
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            client.setRiskAssessment(
                    new RiskAssessmentSettings()
            );

            final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
            rememberDevice.setActive(true);
            mfaSettings.setRememberDevice(rememberDevice);
            rc.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
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
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.REQUIRED, "");
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

            var stepUpAuthenticationRule = "{#request.params['scope'][0].contains('write')}";
            var stepUpAuthentication = new StepUpAuthenticationSettings();
            stepUpAuthentication.setActive(true);
            stepUpAuthentication.setStepUpAuthenticationRule(stepUpAuthenticationRule);
            mfaSettings.setStepUpAuthentication(stepUpAuthentication);

            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
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
            String challengeRule = "{#context.attributes['geoip']['country_iso_code'] == 'FR'}";
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.RISK_BASED, challengeRule);
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "US").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, false);
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
    public void shouldRedirectToMFAChallengePage_adaptiveMFA_2_user_auth() throws Exception {
        router.route().order(-1).handler(rc -> {
            String challenge = "{#context.attributes['geoip']['country_iso_code'] == 'FR'}";
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.CONDITIONAL, challenge);
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "US").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.USER_LOGIN_COMPLETED_KEY, true);
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
            String rule = "{#context.attributes['geoip']['country_iso_code'] == 'FR'}";
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.CONDITIONAL, rule);
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            client.setMfaSettings(mfaSettings);
            rc.session().put(ENROLLED_FACTOR_ID_KEY, FACTOR_ID);

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(PENDING_ACTIVATION);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.USER_LOGIN_COMPLETED_KEY, true);
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
    public void shouldNotRedirectToMFAChallengePage_adaptiveMFA_3_user_stronglyAuth() throws Exception {
        router.route().order(-1).handler(rc -> {
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            client.setMfaSettings(mfaSettings);
            rc.session().put(ENROLLED_FACTOR_ID_KEY, FACTOR_ID);

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.USER_LOGIN_COMPLETED_KEY, true);
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login?scope=read%20write",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldRedirectToMFAChallengePage_adaptiveMFA_3_alternate_factor_id() throws Exception {
        router.route().order(-1).handler(rc -> {
            String rule = "{#context.attributes['geoip']['country_iso_code'] == 'FR'}";
            EnrollSettings enrollSettings = createEnrollSettings(true, false, MfaEnrollType.REQUIRED, 0, "");
            ChallengeSettings challengeSettings = createChallengeSettings(true, MfaChallengeType.CONDITIONAL, rule);
            MFASettings mfaSettings = createMFASettings(enrollSettings, challengeSettings);
            // set client
            Client client = new Client();
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            client.setMfaSettings(mfaSettings);
            rc.session().put(ENROLLED_FACTOR_ID_KEY, FACTOR_ID);
            rc.session().put(ALTERNATIVE_FACTOR_ID_KEY, "factor-2");
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);

            EnrolledFactor enrolledFactor2 = new EnrolledFactor();
            enrolledFactor2.setFactorId("factor-2");
            enrolledFactor2.setStatus(ACTIVATED);

            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(List.of(enrolledFactor, enrolledFactor2));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(ConstantKeys.USER_LOGIN_COMPLETED_KEY, true);
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
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();

            var stepUpAuthenticationRule = "{#request.params['scope'][0] == 'write'}";
            var stepUpAuthentication = new StepUpAuthenticationSettings();
            stepUpAuthentication.setActive(true);
            stepUpAuthentication.setStepUpAuthenticationRule(stepUpAuthenticationRule);
            mfaSettings.setStepUpAuthentication(stepUpAuthentication);

            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
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
            setFactors(client);
            rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            MFASettings mfaSettings = new MFASettings();
            rc.put(ConstantKeys.GEOIP_KEY, new JsonObject().put("country_iso_code", "FR").getMap());
            client.setMfaSettings(mfaSettings);
            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(Collections.singletonList(enrolledFactor));
            rc.getDelegate().setUser(new User(endUser));
            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
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
            when(factorManager.getFactor("factor-2")).thenReturn(recoveryFactor);

            var factorSettings = new FactorSettings();
            var factor = new ApplicationFactorSettings();
            factor.setId(FACTOR_ID);
            factor.setSelectionRule("");
            var factor2 = new ApplicationFactorSettings();
            factor2.setId("factor-2");
            factor2.setSelectionRule("");
            factorSettings.setApplicationFactors(List.of(factor, factor2));
            client.setFactorSettings(factorSettings);

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);
            EnrolledFactor factorRecovery = new EnrolledFactor();
            factorRecovery.setFactorId("factor-2");

            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(List.of(enrolledFactor, factorRecovery));
            rc.getDelegate().setUser(new User(endUser));

            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
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

            var factorSettings = new FactorSettings();
            var factor = new ApplicationFactorSettings();
            factor.setId(FACTOR_ID);
            factor.setSelectionRule("");
            var factor2 = new ApplicationFactorSettings();
            factor2.setId("factor-2");
            factor2.setSelectionRule("");
            factorSettings.setApplicationFactors(List.of(factor, factor2));
            client.setFactorSettings(factorSettings);

            // set user
            EnrolledFactor enrolledFactor = new EnrolledFactor();
            enrolledFactor.setFactorId(FACTOR_ID);
            enrolledFactor.setStatus(ACTIVATED);

            EnrolledFactor factorRecovery = new EnrolledFactor();
            factorRecovery.setFactorId("factor-2");
            factorRecovery.setStatus(ACTIVATED);
            factorRecovery.setSecurity(new EnrolledFactorSecurity(FactorSecurityType.RECOVERY_CODE, null));

            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setFactors(List.of(enrolledFactor, factorRecovery));
            rc.getDelegate().setUser(new User(endUser));

            rc.session().put(STRONG_AUTH_COMPLETED_KEY, true);
            rc.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                HttpStatusCode.OK_200, "OK");
    }

    private void setFactors(Client client) {
        client.setFactors(Collections.singleton(FACTOR_ID));

        var factorSettings = new FactorSettings();
        factorSettings.setDefaultFactorId(DEFAULT_FACTOR_ID);
        var factor1 = new ApplicationFactorSettings();
        factor1.setId("default-factor");
        factor1.setSelectionRule("");
        var factor2 = new ApplicationFactorSettings();
        factor2.setId(FACTOR_ID);
        factor2.setSelectionRule("");
        var factorList = new ArrayList<ApplicationFactorSettings>();
        factorList.add(factor1);
        factorList.add(factor2);
        factorSettings.setApplicationFactors(factorList);

        client.setFactorSettings(factorSettings);
    }

    private  void setFactorsWithRecoveryCode(Client client) {
        var factorSettings = new FactorSettings();
        factorSettings.setDefaultFactorId(DEFAULT_FACTOR_ID);
        var factor1 = new ApplicationFactorSettings();
        factor1.setId("default-factor");
        factor1.setSelectionRule("factor1-selection-rule");

        var factor2 = new ApplicationFactorSettings();
        factor2.setId("factor-id");
        factor2.setSelectionRule("");

        var factor3 = new ApplicationFactorSettings();
        factor3.setId("factor-recovery-code");
        factor3.setSelectionRule("");

        var factorList = new ArrayList<ApplicationFactorSettings>();
        factorList.add(factor1);
        factorList.add(factor2);
        factorList.add(factor3);
        factorSettings.setApplicationFactors(factorList);

        client.setFactorSettings(factorSettings);
    }

    private MFASettings createMFASettings(EnrollSettings enrollSettings, ChallengeSettings challengeSettings) {
        final MFASettings mfaSettings = new MFASettings();
        mfaSettings.setEnroll(enrollSettings);
        mfaSettings.setChallenge(challengeSettings);
        return mfaSettings;
    }

    private ChallengeSettings createChallengeSettings(boolean active, MfaChallengeType type, String challengeRule) {
        final ChallengeSettings settings = new ChallengeSettings();
        settings.setActive(active);
        settings.setType(type);
        settings.setChallengeRule(challengeRule);
        return settings;
    }

    private EnrollSettings createEnrollSettings(boolean active, boolean forceEnrollment, MfaEnrollType type, long skipTime, String enrollmentRule) {
        final EnrollSettings settings = new EnrollSettings();
        settings.setActive(active);
        settings.setForceEnrollment(forceEnrollment);
        settings.setType(type);
        settings.setSkipTimeSeconds(skipTime);
        settings.setEnrollmentRule(enrollmentRule);
        return settings;
    }
}
