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
import io.gravitee.am.model.Device;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DeviceService;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import io.reactivex.Flowable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
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

import static com.google.common.net.HttpHeaders.X_FORWARDED_FOR;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ID;
import static java.lang.String.format;
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

    private static final String ENABLED = "alerts.risk_assessment.settings.%s.enabled";
    @Mock
    private Environment environment;
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
    private Map<String, Boolean> assessments;

    @Before
    public void before() {
        client = new Client();
        client.setDomain("domain-id");
        client.setClientId("client-id");
        user = new User();
        user.setId("user-id");

        routingContext = new SpyRoutingContext();
        handler = new RiskAssessmentHandler(deviceService, userActivityService, eventBus, objectMapper);
        assessments = new HashMap<>(Map.of("ipReputation", false, "geoVelocity", false, "device", false));
    }

    private void whenGetRiskAssessmentSettings() {
        assessments.forEach((assessment, enabled) -> {
            when(environment.getProperty(eq(format(ENABLED, assessment)), any(), any(Boolean.class))).thenReturn(enabled);
            when(environment.getProperty(contains("thresholds"), eq(Double.class))).thenReturn(1.0);
        });
    }

    @Test
    public void must_next_nothing_in_context() {
        whenGetRiskAssessmentSettings();
        handler.handle(routingContext);

        assertTrue(routingContext.verifyNext(1));
    }

    @Test
    public void must_next_only_client_in_context() {
        whenGetRiskAssessmentSettings();
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        handler.handle(routingContext);
        assertTrue(routingContext.verifyNext(1));
    }

    @Test
    public void must_next_only_client_and_user_in_context() {
        whenGetRiskAssessmentSettings();
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        handler.handle(routingContext);
        assertTrue(routingContext.verifyNext(1));
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_disabled() {
        whenGetRiskAssessmentSettings();
        client.setRiskAssessment(new RiskAssessmentSettings());
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        handler.handle(routingContext);
        assertTrue(routingContext.verifyNext(1));
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled() {
        assessments.put("ipReputation", true);
        whenGetRiskAssessmentSettings();
        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

        handler.handle(routingContext);
        verify(eventBus, times(1)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled_with_remember_device() {
        assessments.put("device", true);
        whenGetRiskAssessmentSettings();
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
        assessments.put("ipReputation", true);
        whenGetRiskAssessmentSettings();

        routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
        routingContext.request().headers().set(X_FORWARDED_FOR, "192.168.0.1");

        handler.handle(routingContext);

        verify(eventBus, times(1)).request(
                any(), anyString(), (Handler<AsyncResult<Message<Object>>>) Mockito.any());
    }

    @Test
    public void must_next_only_client_and_user_with_risk_assessment_enabled_with_geo_velocity() {
        assessments.put("geoVelocity", true);
        whenGetRiskAssessmentSettings();

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
