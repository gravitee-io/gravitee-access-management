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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.common.auth.AuthenticationDetails;
import io.gravitee.am.gateway.handler.common.service.UserActivityGatewayService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.model.Device;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.service.DeviceService;
import io.gravitee.risk.assessment.api.assessment.AssessmentMessageResult;
import io.gravitee.risk.assessment.api.assessment.AssessmentResult;
import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.eventbus.EventBus;
import io.vertx.rxjava3.core.eventbus.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.gravitee.risk.assessment.api.assessment.Assessment.HIGH;
import static io.gravitee.risk.assessment.api.assessment.Assessment.LOW;
import static io.gravitee.risk.assessment.api.assessment.Assessment.MEDIUM;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private Vertx vertx;
    @Mock
    private DeviceService deviceService;
    @Mock
    private UserActivityGatewayService userActivityService;
    @Mock
    private EventBus eventBus;

    @Mock
    private Message<String> message;

    private RiskAssessmentService riskAssessmentService;

    @Before
    public void setUp() throws Exception {
        when(vertx.eventBus()).thenReturn(eventBus);
    }

    @Test
    public void must_test_not_computeRiskAssessment_disabled() {
        riskAssessmentService = new RiskAssessmentService(deviceService, userActivityService, objectMapper, new RiskAssessmentSettings(), vertx);
        var testObserver = riskAssessmentService.computeRiskAssessment(authDetailsStub()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();

        verify(eventBus, times(0)).request(
                any(), anyString());

        verify(eventBus, times(0)).request(
                any(), anyString());
    }

    @Test
    public void must_test_not_computeRiskAssessment_enabled_but_not_other_assessment() throws JsonProcessingException {
        var settings = new RiskAssessmentSettings()
                .setEnabled(true)
                .setDeviceAssessment(new AssessmentSettings().setEnabled(false))
                .setIpReputationAssessment(new AssessmentSettings().setEnabled(false))
                .setGeoVelocityAssessment(new AssessmentSettings().setEnabled(false));

        when(message.body()).thenReturn(objectMapper.writeValueAsString(new AssessmentMessageResult()));
        when(eventBus.<String>rxRequest(anyString(), any())).thenReturn(Single.just(message));
        riskAssessmentService = new RiskAssessmentService(deviceService, userActivityService, objectMapper, settings, vertx);
        var testObserver = riskAssessmentService.computeRiskAssessment(authDetailsStub()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(Objects::nonNull);

        verify(eventBus, times(1)).rxRequest(
                any(), anyString());

        verify(eventBus, times(1)).rxRequest(
                any(), anyString());
    }

    @Test
    public void must_test_computeRiskAssessment() throws JsonProcessingException {
        var settings = new RiskAssessmentSettings()
                .setEnabled(true)
                .setDeviceAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 1.0D)))
                .setIpReputationAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 30D)))
                .setGeoVelocityAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 5.0D / 18D)));

        when(message.body()).thenReturn(objectMapper.writeValueAsString(
                new AssessmentMessageResult()
                        .setDevices(new AssessmentResult<Double>().setResult(58d).setAssessment(MEDIUM))
                        .setGeoVelocity(new AssessmentResult<Double>().setResult(1000d).setAssessment(LOW))
                        .setIpReputation(new AssessmentResult<Double>().setResult(60d).setAssessment(HIGH))
        ));
        when(eventBus.<String>rxRequest(anyString(), any())).thenReturn(Single.just(message));

        riskAssessmentService = new RiskAssessmentService(deviceService, userActivityService, objectMapper, settings, vertx);

        //device
        doReturn(Flowable.just(new Device().setDeviceId("1"), new Device().setDeviceId("2")))
                .when(deviceService).findByDomainAndUser(anyString(), any());
        //geo
        doReturn(Flowable.just(
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date()),
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date())
        )).when(userActivityService).findByDomainAndTypeAndUserAndLimit(any(Domain.class), any(), anyString(), eq(2));

        var testObserver = riskAssessmentService.computeRiskAssessment(authDetailsStub()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(Objects::nonNull);

        verify(eventBus, times(1)).rxRequest(
                any(), anyString());

        verify(eventBus, times(1)).rxRequest(
                any(), anyString());
    }

    @Test
    public void must_test_return_nothing_due_to_event_bus_error() throws JsonProcessingException {
        var settings = new RiskAssessmentSettings()
                .setEnabled(true)
                .setDeviceAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 1.0D)))
                .setIpReputationAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 30D)))
                .setGeoVelocityAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 5.0D / 18D)));


        when(eventBus.<String>rxRequest(anyString(), any())).thenReturn(Single.error(new IllegalArgumentException()));

        riskAssessmentService = new RiskAssessmentService(deviceService, userActivityService, objectMapper, settings, vertx);

        //device
        doReturn(Flowable.just(new Device().setDeviceId("1"), new Device().setDeviceId("2")))
                .when(deviceService).findByDomainAndUser(anyString(), any());
        //geo
        doReturn(Flowable.just(
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date()),
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date())
        )).when(userActivityService).findByDomainAndTypeAndUserAndLimit(any(Domain.class), any(), anyString(), eq(2));

        var testObserver = riskAssessmentService.computeRiskAssessment(authDetailsStub()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();

        verify(eventBus, times(1)).rxRequest(
                any(), anyString());

        verify(eventBus, times(1)).rxRequest(
                any(), anyString());
    }

    @Test
    public void must_test_return_nothing_due_to_message_parse_error() throws JsonProcessingException {
        var settings = new RiskAssessmentSettings()
                .setEnabled(true)
                .setDeviceAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 1.0D)))
                .setIpReputationAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 30D)))
                .setGeoVelocityAssessment(new AssessmentSettings().setEnabled(true).setThresholds(Map.of(LOW, 5.0D / 18D)));

        when(message.body()).thenReturn("{ wrong format }");
        when(eventBus.<String>rxRequest(anyString(), any())).thenReturn(Single.just(message));

        riskAssessmentService = new RiskAssessmentService(deviceService, userActivityService, objectMapper, settings, vertx);

        //device
        doReturn(Flowable.just(new Device().setDeviceId("1"), new Device().setDeviceId("2")))
                .when(deviceService).findByDomainAndUser(anyString(), any());
        //geo
        doReturn(Flowable.just(
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date()),
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date())
        )).when(userActivityService).findByDomainAndTypeAndUserAndLimit(any(Domain.class), any(), anyString(), eq(2));

        var testObserver = riskAssessmentService.computeRiskAssessment(authDetailsStub()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(Objects::nonNull);

        verify(eventBus, times(1)).rxRequest(
                any(), anyString());

        verify(eventBus, times(1)).rxRequest(
                any(), anyString());
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
