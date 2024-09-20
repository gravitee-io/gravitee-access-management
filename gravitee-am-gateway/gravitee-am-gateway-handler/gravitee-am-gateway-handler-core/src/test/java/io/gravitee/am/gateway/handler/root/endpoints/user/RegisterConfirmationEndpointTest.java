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
package io.gravitee.am.gateway.handler.root.endpoints.user;

import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.register.RegisterConfirmationEndpoint;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Test;
import org.mockito.Mockito;

import static io.gravitee.am.common.utils.ConstantKeys.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterConfirmationEndpointTest {

    private final DeviceIdentifierManager deviceIdentifierManager = mock(DeviceIdentifierManager.class);

    @Test
    public void must_render_engine_with_encoded_client_id() {
        final Domain domain = new Domain();
        domain.setId("domain-id");

        final Buffer buffer = mock(Buffer.class);
        final ThymeleafTemplateEngine engine = mock(ThymeleafTemplateEngine.class);
        when(engine.render(anyMap(), anyString())).thenReturn(Single.just(buffer));
        var registrationConfirmation = new RegisterConfirmationEndpoint(engine, domain, deviceIdentifierManager);

        final SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        var routingContext = Mockito.spy(ctx);
        final Client client = new Client();
        client.setClientId("some # clientId");
        routingContext.put(CLIENT_CONTEXT_KEY, client);
        registrationConfirmation.handle(routingContext);


        assertTrue(routingContext.<String>get(ACTION_KEY).contains("client_id=some+%23+clientId"));
        verify(engine, times(1)).render(anyMap(), anyString());
    }

    @Test
    public void must_render_engine_with_regular_client_id() {
        final Domain domain = new Domain();
        domain.setId("domain-id");

        final Buffer buffer = mock(Buffer.class);
        final ThymeleafTemplateEngine engine = mock(ThymeleafTemplateEngine.class);
        when(engine.render(anyMap(), anyString())).thenReturn(Single.just(buffer));
        var registrationConfirmation = new RegisterConfirmationEndpoint(engine, domain, deviceIdentifierManager);

        final SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);

        var routingContext = Mockito.spy(ctx);
        final Client client = new Client();
        client.setClientId("some__clientId");
        routingContext.put(CLIENT_CONTEXT_KEY, client);
        registrationConfirmation.handle(routingContext);

        assertTrue(routingContext.<String>get(ACTION_KEY).contains("client_id=some__clientId"));
        verify(engine, times(1)).render(anyMap(), anyString());
    }

    @Test
    public void must_not_render_due_to_existing_registered_user() {
        final Domain domain = new Domain();
        domain.setId("domain-id");

        final ThymeleafTemplateEngine engine = mock(ThymeleafTemplateEngine.class);
        var registrationConfirmation = new RegisterConfirmationEndpoint(engine, domain, deviceIdentifierManager);

        final SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        var routingContext = Mockito.spy(ctx);

        final User user = mock(User.class);
        when(user.isPreRegistration()).thenReturn(true);
        when(user.isRegistrationCompleted()).thenReturn(true);
        routingContext.put(USER_CONTEXT_KEY, user);


        final Client client = new Client();
        client.setClientId("some__clientId");
        routingContext.put(CLIENT_CONTEXT_KEY, client);
        registrationConfirmation.handle(routingContext);

        assertNull(routingContext.<String>get(ACTION_KEY));
        verify(engine, times(0)).render(anyMap(), anyString());
    }
}
