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
import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.reactivex.Flowable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.Map;

import static io.gravitee.risk.assessment.api.assessment.Assessment.LOW;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)

/**
 * @author Michael CARTER (michael.carter at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RiskAssessmentServiceTest {

    @Mock
    private Vertx vertx;
    @Mock
    private DeviceService deviceService;
    @Mock
    private UserActivityService userActivityService;
    @Mock
    private EventBus eventBus;

    private RiskAssessmentService riskAssessmentService;

    @Before
    public void setUp() throws Exception {
        when(vertx.eventBus()).thenReturn(eventBus);
    }

    @Test
    public void must_test_not_computeRiskAssessment_disabled() {
        riskAssessmentService = new RiskAssessmentService(deviceService, userActivityService, new ObjectMapper(), new RiskAssessmentSettings(), vertx);
        var testObserver = riskAssessmentService.computeRiskAssessment(authDetailsStub(), Assert::assertNotNull).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(eventBus, times(0)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
    }

    @Test
    public void must_test_not_computeRiskAssessment_enabled_but_not_other_assessment() {
        var settings = new RiskAssessmentSettings()
                .setEnabled(true)
                .setDeviceAssessment(new AssessmentSettings().setEnabled(false))
                .setIpReputationAssessment(new AssessmentSettings().setEnabled(false))
                .setGeoVelocityAssessment(new AssessmentSettings().setEnabled(false));

        riskAssessmentService = new RiskAssessmentService(deviceService, userActivityService, new ObjectMapper(), settings, vertx);
        var testObserver = riskAssessmentService.computeRiskAssessment(authDetailsStub(), Assert::assertNotNull).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(eventBus, times(1)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
    }

    @Test
    public void must_test_computeRiskAssessment() {
        var settings = new RiskAssessmentSettings()
                .setEnabled(true)
                .setDeviceAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 1.0D)))
                .setIpReputationAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 30D)))
                .setGeoVelocityAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 5.0D / 18D)));

        riskAssessmentService = new RiskAssessmentService(deviceService, userActivityService, new ObjectMapper(), settings, vertx);

        //device
        doReturn(Flowable.just(new Device().setDeviceId("1"), new Device().setDeviceId("2")))
                .when(deviceService).findByDomainAndUser(anyString(), anyString());
        //geo
        doReturn(Flowable.just(
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date()),
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date())
        )).when(userActivityService).findByDomainAndTypeAndUserAndLimit(anyString(), any(), anyString(), eq(2));

        var testObserver = riskAssessmentService.computeRiskAssessment(authDetailsStub(), Assert::assertNotNull).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(eventBus, times(1)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
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