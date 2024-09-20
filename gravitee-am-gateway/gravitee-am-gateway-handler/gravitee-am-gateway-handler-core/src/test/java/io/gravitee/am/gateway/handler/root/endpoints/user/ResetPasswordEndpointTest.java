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

import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.resources.endpoint.user.password.ResetPasswordEndpoint;
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

import static io.gravitee.am.common.utils.ConstantKeys.ACTION_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResetPasswordEndpointTest {

    private final DeviceIdentifierManager deviceIdentifierManager = mock(DeviceIdentifierManager.class);

    @Test
    public void must_render_engine_with_encoded_client_id() {
        final Domain domain = new Domain();
        domain.setId("domain-id");

        final Buffer buffer = mock(Buffer.class);
        final ThymeleafTemplateEngine engine = mock(ThymeleafTemplateEngine.class);
        when(engine.render(anyMap(), anyString())).thenReturn(Single.just(buffer));

        final PasswordPolicyManager passwordPolicyManager = mock(PasswordPolicyManager.class);
        final IdentityProviderManager identityProviderManager = mock(IdentityProviderManager.class);
        var resetPassword = new ResetPasswordEndpoint(engine, domain, passwordPolicyManager, identityProviderManager, deviceIdentifierManager);

        final SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.GET);

        var routingContext = Mockito.spy(ctx);
        final Client client = new Client();
        client.setClientId("some # clientId");
        routingContext.put(CLIENT_CONTEXT_KEY, client);
        routingContext.put(USER_CONTEXT_KEY, new User());
        resetPassword.handle(routingContext);


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

        final PasswordPolicyManager passwordPolicyManager = mock(PasswordPolicyManager.class);
        final IdentityProviderManager identityProviderManager = mock(IdentityProviderManager.class);
        var resetPassword = new ResetPasswordEndpoint(engine, domain, passwordPolicyManager, identityProviderManager, deviceIdentifierManager);

        final SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.GET);
        var routingContext = Mockito.spy(ctx);
        final Client client = new Client();
        client.setClientId("some__clientId");
        routingContext.put(CLIENT_CONTEXT_KEY, client);
        routingContext.put(USER_CONTEXT_KEY, new User());
        resetPassword.handle(routingContext);

        assertTrue(routingContext.<String>get(ACTION_KEY).contains("client_id=some__clientId"));
        verify(engine, times(1)).render(anyMap(), anyString());
    }
}
