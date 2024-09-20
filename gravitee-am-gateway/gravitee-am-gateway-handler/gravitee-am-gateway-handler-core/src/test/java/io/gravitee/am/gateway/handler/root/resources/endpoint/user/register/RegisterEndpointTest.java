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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.register;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class RegisterEndpointTest {

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private Domain domain;

    @Mock
    private BotDetectionManager botDetectionManager;
    @Mock
    private PasswordPolicyManager passwordPolicyManager;
    @Mock
    private IdentityProviderManager identityProviderManager;
    @Mock
    private DeviceIdentifierManager deviceIdentifierManager;
    private RegisterEndpoint registerEndpoint;
    private SpyRoutingContext context;

    @BeforeEach
    public void setup() {
        when(botDetectionManager.getTemplateVariables(any(), any())).thenReturn(Map.of());
        registerEndpoint = new RegisterEndpoint(templateEngine, domain, botDetectionManager, passwordPolicyManager, identityProviderManager, deviceIdentifierManager);
        context = new SpyRoutingContext();
        context.setMethod(HttpMethod.GET);
    }

    @Test
    @DisplayName("Must successfully go through RegisterEndpoint with account settings")
    public void must_go_through_RegisterEndpoint_with_no_configuration() {
        final AccountSettings accountSettings = new AccountSettings();
        accountSettings.setSendVerifyRegistrationAccountEmail(true);

        when(templateEngine.render(anyMap(), anyString())).thenReturn(Single.just(new Buffer(BufferImpl.buffer())));
        when(domain.getAccountSettings()).thenReturn(accountSettings);

        registerEndpoint.handle(context);

        Awaitility.await().until(() -> context.ended());

        assertTrue(context.<Boolean>get(ConstantKeys.TEMPLATE_VERIFY_REGISTRATION_ACCOUNT_KEY));
    }

    @Test
    @DisplayName("Must successfully go through RegisterEndpoint with no configuration")
    public void must_go_through_RegisterEndpoint_with_configuration() {
        when(templateEngine.render(anyMap(), anyString())).thenReturn(Single.just(new Buffer(BufferImpl.buffer())));

        registerEndpoint.handle(context);

        Awaitility.await().until(() -> context.ended());
    }

    @Test
    @DisplayName("Must fail due to failed render")
    public void must_fail_due_to_failed_render() {
        when(templateEngine.render(anyMap(), anyString())).thenReturn(Single.error(new RuntimeException("Could not render template")));

        registerEndpoint.handle(context);

        Awaitility.await().until(() -> context.failed());
    }
}
