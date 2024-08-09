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

package io.gravitee.am.gateway.handler.risk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.dummies.SpyRoutingContext;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.risk.RiskAssessmentHandler;
import io.gravitee.am.model.Device;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DeviceService;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.risk.assessment.api.assessment.Assessment;
import io.gravitee.risk.assessment.api.assessment.AssessmentMessageResult;
import io.gravitee.risk.assessment.api.assessment.AssessmentResult;
import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.eventbus.EventBus;
import io.vertx.rxjava3.core.eventbus.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ID;
import static io.gravitee.risk.assessment.api.assessment.Assessment.HIGH;
import static io.gravitee.risk.assessment.api.assessment.Assessment.MEDIUM;
import static io.gravitee.risk.assessment.api.assessment.Assessment.NONE;
import static io.gravitee.risk.assessment.api.assessment.Assessment.SAFE;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RiskAssessmentHandlerTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private UserActivityService userActivityService;

    @Mock
    private EventBus eventBus;

    private ObjectMapper objectMapper = new ObjectMapper();

    private RiskAssessmentHandler handler;
    private SpyRoutingContext routingContext;
    private Client client;
    private User user;

    @Before
    public void before() {
        client = new Client();
        client.setDomain("domain-id");
        client.setClientId("client-id");
        user = new User();
        user.setId("user-id");

        routingContext = new SpyRoutingContext();
        handler = new RiskAssessmentHandler(deviceService, userActivityService, eventBus, objectMapper);
    }

    @Test
    public void must_next_nothing_in_context() {
        handler.handle(routingContext);

        assertTrue(routingContext.verifyNext(1));
    }

    @Test
    public void must_next_only_client_in_context() {
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        handler.handle(routingContext);
        assertTrue(routingContext.verifyNext(1));
    }

    @Test
    public void must_next_only_client_and_user_in_context() {
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        handler.handle(routingContext);
        assertTrue(routingContext.verifyNext(1));
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_disabled() {
        client.setRiskAssessment(new RiskAssessmentSettings());
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        handler.handle(routingContext);
        assertTrue(routingContext.verifyNext(1));
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled() {
        final RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        client.setRiskAssessment(riskAssessment);
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        handler.handle(routingContext);
        verify(eventBus, times(1)).request(
                any(), anyString());
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled_with_remember_device() throws JsonProcessingException {
        final RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        riskAssessment.setDeviceAssessment(new AssessmentSettings().setEnabled(true));
        client.setRiskAssessment(riskAssessment);
        final MFASettings mfaSettings = new MFASettings();

        final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        mfaSettings.setRememberDevice(rememberDevice);
        client.setMfaSettings(mfaSettings);

        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
        routingContext.session().put(DEVICE_ID, "deviceId");

        var mockMessage = Mockito.mock(Message.class);
        when(mockMessage.body()).thenReturn(returnMessageResult(SAFE, NONE, NONE));
        when(eventBus.request(anyString(), anyString())).thenReturn(Single.just(mockMessage));

        doReturn(Flowable.just(new Device().setDeviceId("1"), new Device().setDeviceId("2")))
                .when(deviceService).findByDomainAndUser(anyString(), any());

        handler.handle(routingContext);

        verify(eventBus, times(1)).request(anyString(), any());
        assertNotNull(routingContext.session().get(ConstantKeys.RISK_ASSESSMENT_KEY));
    }

    @Test
    public void must_do_nothing_when_error_with_deviceService() throws JsonProcessingException {
        final RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        riskAssessment.setDeviceAssessment(new AssessmentSettings().setEnabled(true));
        client.setRiskAssessment(riskAssessment);
        final MFASettings mfaSettings = new MFASettings();

        final RememberDeviceSettings rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        mfaSettings.setRememberDevice(rememberDevice);
        client.setMfaSettings(mfaSettings);

        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
        routingContext.session().put(DEVICE_ID, "deviceId");

        doReturn(Flowable.error(new IllegalArgumentException()))
                .when(deviceService).findByDomainAndUser(anyString(), any());

        handler.handle(routingContext);

        verify(eventBus, times(0)).request(anyString(), any());
        assertNull(routingContext.session().get(ConstantKeys.RISK_ASSESSMENT_KEY));
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled_with_ip_reputation() throws JsonProcessingException {
        final RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        riskAssessment.setIpReputationAssessment(new AssessmentSettings().setEnabled(true));
        client.setRiskAssessment(riskAssessment);

        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
        routingContext.request().headers().set(X_FORWARDED_FOR, "192.168.0.1");

        var mockMessage = Mockito.mock(Message.class);
        when(mockMessage.body()).thenReturn(returnMessageResult(NONE, MEDIUM, NONE));
        when(eventBus.request(anyString(), anyString())).thenReturn(Single.just(mockMessage));

        handler.handle(routingContext);

        verify(eventBus, times(1)).request(anyString(), any());
        assertNotNull(routingContext.session().get(ConstantKeys.RISK_ASSESSMENT_KEY));
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled_with_geo_velocity() throws JsonProcessingException {
        final RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        riskAssessment.setGeoVelocityAssessment(new AssessmentSettings().setEnabled(true));
        client.setRiskAssessment(riskAssessment);

        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        doReturn(Flowable.just(
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date()),
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date())
        )).when(userActivityService).findByDomainAndTypeAndUserAndLimit(anyString(), any(), anyString(), eq(2));

        var mockMessage = Mockito.mock(Message.class);
        when(mockMessage.body()).thenReturn(returnMessageResult(NONE, NONE, HIGH));
        when(eventBus.request(anyString(), anyString())).thenReturn(Single.just(mockMessage));

        handler.handle(routingContext);

        verify(eventBus, times(1)).request(anyString(), any());
        assertNotNull(routingContext.session().get(ConstantKeys.RISK_ASSESSMENT_KEY));
    }

    @Test
    public void must_continue_when_an_error_occurs_on_userActivityService() throws JsonProcessingException {
        final RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        riskAssessment.setGeoVelocityAssessment(new AssessmentSettings().setEnabled(true));
        client.setRiskAssessment(riskAssessment);

        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        doReturn(Flowable.error(new IllegalArgumentException())).when(userActivityService).findByDomainAndTypeAndUserAndLimit(anyString(), any(), anyString(), eq(2));

        handler.handle(routingContext);

        verify(eventBus, times(0)).request(anyString(), any());
        assertNull(routingContext.session().get(ConstantKeys.RISK_ASSESSMENT_KEY));
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled_with_wrongly_formatted_result() throws JsonProcessingException {
        final RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        riskAssessment.setGeoVelocityAssessment(new AssessmentSettings().setEnabled(true));
        client.setRiskAssessment(riskAssessment);

        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        doReturn(Flowable.just(
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date()),
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date())
        )).when(userActivityService).findByDomainAndTypeAndUserAndLimit(anyString(), any(), anyString(), eq(2));

        var mockMessage = Mockito.mock(Message.class);
        when(mockMessage.body()).thenReturn("{ wrong message }");
        when(eventBus.request(anyString(), anyString())).thenReturn(Single.just(mockMessage));

        handler.handle(routingContext);

        verify(eventBus, times(1)).request(anyString(), any());
        assertNotNull(routingContext.session().get(ConstantKeys.RISK_ASSESSMENT_KEY));
    }

    @Test
    public void must_do_nothing_when_event_bus_returns_error() throws JsonProcessingException {
        final RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        riskAssessment.setGeoVelocityAssessment(new AssessmentSettings().setEnabled(true));
        client.setRiskAssessment(riskAssessment);

        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        doReturn(Flowable.just(
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date()),
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date())
        )).when(userActivityService).findByDomainAndTypeAndUserAndLimit(anyString(), any(), anyString(), eq(2));

        when(eventBus.request(anyString(), anyString())).thenReturn(Single.error(new IllegalArgumentException("Cannot get message")));

        handler.handle(routingContext);

        verify(eventBus, times(1)).request(anyString(), any());
        assertNull(routingContext.session().get(ConstantKeys.RISK_ASSESSMENT_KEY));
    }

    private String returnMessageResult(Assessment devices, Assessment geoVelocity, Assessment ipReputation) throws JsonProcessingException {
        var message = new AssessmentMessageResult()
                .setDevices(getAssessmentResult(devices, 3D))
                .setGeoVelocity(getAssessmentResult(geoVelocity, 2500D))
                .setIpReputation(getAssessmentResult(ipReputation, 70D));
        return objectMapper.writeValueAsString(message);
    }

    private AssessmentResult<Double> getAssessmentResult(Assessment devices, double v) {
        return new AssessmentResult<Double>().setResult(!NONE.equals(devices) ? v : null).setAssessment(devices);
    }
}
