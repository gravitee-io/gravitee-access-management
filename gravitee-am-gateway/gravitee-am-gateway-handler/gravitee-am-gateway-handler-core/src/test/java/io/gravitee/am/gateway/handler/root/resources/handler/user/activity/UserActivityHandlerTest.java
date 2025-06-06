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

package io.gravitee.am.gateway.handler.root.resources.handler.user.activity;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.common.service.UserActivityGatewayService;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserActivity.Type;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.rxjava3.core.Completable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.GEOIP_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.LOGIN_ATTEMPT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_IP_LOCATION;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_USER_AGENT;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserActivityHandlerTest {

    @Mock
    private UserActivityGatewayService userActivityService;

    private UserActivityHandler userActivityHandler;
    private Client client;
    private User user;
    private Domain domain;
    private SpyRoutingContext routingContext;

    @Before
    public void setUp() {
        userActivityHandler = new UserActivityHandler(userActivityService, domain);

        client = new Client();
        client.setDomain("domain-id");
        client.setClientId("client-id");
        user = new User();
        user.setReferenceId("domain-id");
        user.setId("user-id");
        domain = new Domain();
        domain.setId(user.getReferenceId());

        routingContext = new SpyRoutingContext();
        routingContext.setUser(new io.vertx.rxjava3.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
        doReturn(true).when(userActivityService).canSaveUserActivity();
        doReturn(Completable.complete()).when(userActivityService).save(any(), anyString(), eq(Type.LOGIN), any());
    }

    @Test
    public void must_do_next_due_to_empty_user() {
        routingContext.setUser(null);
        userActivityHandler.handle(routingContext);

        assertTrue(routingContext.verifyNext(1));
        verify(userActivityService, times(0)).save(any(), anyString(), eq(Type.LOGIN), any());
    }

    @Test
    public void must_do_next_due_to_user_activity_not_allowed() {
        doReturn(false).when(userActivityService).canSaveUserActivity();
        userActivityHandler.handle(routingContext);

        assertTrue(routingContext.verifyNext(1));
        verify(userActivityService, times(0)).save(any(), anyString(), eq(Type.LOGIN), any());
    }

    @Test
    public void must_save_user_activity_with_no_data_and_do_next() {
        userActivityHandler.handle(routingContext);

        assertTrue(routingContext.verifyNext(1));
        verify(userActivityService, times(1)).save(any(), anyString(), eq(Type.LOGIN), any());
    }

    @Test
    public void must_save_user_activity_with_geoip_and_do_next() {
        routingContext.session().put(USER_CONSENT_IP_LOCATION, true);
        routingContext.put(GEOIP_KEY, Map.of("lon", 125, "lat", 75));
        userActivityHandler.handle(routingContext);

        assertTrue(routingContext.verifyNext(1));
        verify(userActivityService, times(1)).save(any(), anyString(), eq(Type.LOGIN), any());
    }

    @Test
    public void must_save_user_activity_with_useragent_and_do_next() {
        routingContext.session().put(USER_CONSENT_USER_AGENT, true);
        routingContext.request().headers().set(HttpHeaders.USER_AGENT, "some user agent");
        userActivityHandler.handle(routingContext);

        assertTrue(routingContext.verifyNext(1));
        verify(userActivityService, times(1)).save(any(), anyString(), eq(Type.LOGIN), any());
    }

    @Test
    public void must_save_user_activity_with_login_attempt_and_do_next() {
        routingContext.session().put(LOGIN_ATTEMPT_KEY, 30);
        userActivityHandler.handle(routingContext);

        assertTrue(routingContext.verifyNext(1));
        verify(userActivityService, times(1)).save(any(), anyString(), eq(Type.LOGIN), any());
    }

    @Test
    public void must_save_user_activity_with_login_attempt_long_value_and_do_next() {
        routingContext.session().put(LOGIN_ATTEMPT_KEY, 30L);
        userActivityHandler.handle(routingContext);

        assertTrue(routingContext.verifyNext(1));
        verify(userActivityService, times(1)).save(any(), anyString(), eq(Type.LOGIN), any());
    }


    @Test
    public void must_save_user_activity_with_all_and_do_next() {
        routingContext.session().put(USER_CONSENT_IP_LOCATION, true);
        routingContext.session().put(USER_CONSENT_USER_AGENT, true);
        routingContext.put(GEOIP_KEY, Map.of("lon", 125, "lat", 75));
        routingContext.request().headers().set(HttpHeaders.USER_AGENT, "some user agent");
        routingContext.session().put(LOGIN_ATTEMPT_KEY, 30);
        userActivityHandler.handle(routingContext);

        assertTrue(routingContext.verifyNext(1));
        verify(userActivityService, times(1)).save(any(), anyString(), eq(Type.LOGIN), any());
    }

    @Test
    public void must_log_error_and_do_next() {
        routingContext.session().put(GEOIP_KEY, Map.of("lon", 125, "lat", 75));
        routingContext.session().put(Claims.USER_AGENT, "some user agent");
        routingContext.session().put(LOGIN_ATTEMPT_KEY, 30);

        doReturn(Completable.error(new IllegalArgumentException("An unexpected error has occurred")))
                .when(userActivityService).save(any(), anyString(), eq(Type.LOGIN), any());

        userActivityHandler.handle(routingContext);

        assertTrue(routingContext.verifyNext(1));
        verify(userActivityService, times(1)).save(any(), anyString(), eq(Type.LOGIN), any());
    }
}
