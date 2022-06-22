package io.gravitee.am.gateway.handler.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.common.auth.AuthenticationDetails;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.model.Device;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.service.DeviceService;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.reactivex.Flowable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@RunWith(MockitoJUnitRunner.class)
public class RiskAssessmentHelperTest {

    private static final String ENABLED = "alerts.risk_assessment.settings.%s.enabled";
    @Mock
    private Vertx vertx;
    @Mock
    private Environment environment;
    @Mock
    private DeviceService deviceService;
    @Mock
    private UserActivityService userActivityService;
    @Mock
    private EventBus eventBus;

    private Map<String, Boolean> assessments;
    private RiskAssessmentHelper riskAssessmentHelper;

    @Before
    public void setUp() throws Exception {
        when(vertx.eventBus()).thenReturn(eventBus);
        riskAssessmentHelper = new RiskAssessmentHelper(deviceService, environment, userActivityService, new ObjectMapper(), vertx);
        assessments = new HashMap<>(Map.of("ipReputation", true, "geoVelocity", true, "device", true));
        whenGetRiskAssessmentSettings();
    }

    @SuppressWarnings("all")
    @Test
    public void computeRiskAssessment() {
        //device
        doReturn(Flowable.just(new Device().setDeviceId("1"), new Device().setDeviceId("2")))
                .when(deviceService).findByDomainAndUser(anyString(), anyString());
        //geo
        doReturn(Flowable.just(
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date()),
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date())
        )).when(userActivityService).findByDomainAndTypeAndUserAndLimit(anyString(), any(), anyString(), eq(2));
        RiskAssessmentSettings riskAssessmentSettings = riskAssessmentHelper.getRiskAssessmentSettings();
        riskAssessmentHelper.computeRiskAssessment(authDetailsStub(), assessmentMessageResult -> System.out.println("yes"), riskAssessmentSettings);
        verify(eventBus, times(1)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
    }

    private void whenGetRiskAssessmentSettings() {
        assessments.forEach((assessment, enabled) -> {
            when(environment.getProperty(eq(format(ENABLED, assessment)), any(), any(Boolean.class))).thenReturn(enabled);
            when(environment.getProperty(contains("thresholds"), eq(Double.class))).thenReturn(1.0);
        });
    }

    private AuthenticationDetails authDetailsStub() {
        return new AuthenticationDetails() {
            @Override
            public Domain getDomain() {
                return new Domain() {
                    @Override
                    public String getId() {
                        return "1234";
                    }
                };
            }

            @Override
            public User getUser() {
                return new User() {
                    @Override
                    public String getId() {
                        return "1234";
                    }
                };
            }

            @Override
            public Authentication getPrincipal() {
                Authentication principal = mock(Authentication.class);
                AuthenticationContext context = mock(AuthenticationContext.class);
                given(context.get(anyString())).willReturn("myDeviceId");
                given(principal.getContext()).willReturn(context);
                return principal;
            }
        };
    }
}