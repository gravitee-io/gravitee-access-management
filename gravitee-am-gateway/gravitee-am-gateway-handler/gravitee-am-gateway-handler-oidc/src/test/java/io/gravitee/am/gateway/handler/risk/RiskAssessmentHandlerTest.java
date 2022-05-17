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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.dummies.SpyRoutingContext;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.risk.RiskAssessmentHandler;
import io.gravitee.am.model.*;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DeviceService;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.reactivex.Flowable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import java.util.Date;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ID;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
        routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        handler.handle(routingContext);
        assertTrue(routingContext.verifyNext(1));
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_disabled() {
        client.setRiskAssessment(new RiskAssessmentSettings());
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        handler.handle(routingContext);
        assertTrue(routingContext.verifyNext(1));
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled() {
        final RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        client.setRiskAssessment(riskAssessment);
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        handler.handle(routingContext);
        verify(eventBus, times(1)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled_with_remember_device() {
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
        routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
        routingContext.session().put(DEVICE_ID, "deviceId");

        doReturn(Flowable.just(new Device().setDeviceId("1"), new Device().setDeviceId("2")))
                .when(deviceService).findByDomainAndUser(anyString(), anyString());

        handler.handle(routingContext);

        verify(eventBus, times(1)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled_with_ip_reputation() {
        final RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        riskAssessment.setIpReputationAssessment(new AssessmentSettings().setEnabled(true));
        client.setRiskAssessment(riskAssessment);

        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
        routingContext.request().headers().set(X_FORWARDED_FOR, "192.168.0.1");

        handler.handle(routingContext);

        verify(eventBus, times(1)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled_with_geo_velocity() {
        final RiskAssessmentSettings riskAssessment = new RiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        riskAssessment.setGeoVelocityAssessment(new AssessmentSettings().setEnabled(true));
        client.setRiskAssessment(riskAssessment);

        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        doReturn(Flowable.just(
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date()),
                new UserActivity().setLatitude(50.34D).setLongitude(3.025D).setCreatedAt(new Date())
        )).when(userActivityService).findByDomainAndTypeAndUserAndLimit(anyString(), any(), anyString(), eq(2));

        handler.handle(routingContext);

        verify(eventBus, times(1)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
    }
}
