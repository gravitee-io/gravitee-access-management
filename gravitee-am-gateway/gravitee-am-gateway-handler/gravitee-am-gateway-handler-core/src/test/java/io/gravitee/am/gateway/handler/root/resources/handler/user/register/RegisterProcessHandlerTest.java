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
package io.gravitee.am.gateway.handler.root.resources.handler.user.register;

import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.gateway.handler.root.service.response.RegistrationResponse;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.REGISTRATION_RESPONSE_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class RegisterProcessHandlerTest {

    @Mock
    private UserService userService;
    @Mock
    private Domain domain;

    private SpyRoutingContext context;
    private RegisterProcessHandler registerProcessHandler;

    @BeforeEach
    public void setUp() {
        registerProcessHandler = new RegisterProcessHandler(userService, domain);
        context = new SpyRoutingContext("/register");
        context.put(CONTEXT_PATH, "");
    }

    @Test
    public void must_initialize_client_in_user_profile() {
        Client client = new Client();
        client.setId(UUID.randomUUID().toString());
        context.put(CLIENT_CONTEXT_KEY, client);

        RegistrationResponse registrationResponse = new RegistrationResponse();
        registrationResponse.setUser(new User());
        when(userService.register(any(), any(), any(), any())).thenReturn(Single.just(registrationResponse));

        registerProcessHandler.handle(context);

        verify(userService).register(any(), argThat(user -> client.getId().equals(user.getClient())), any(), any());
        context.verifyNext(1);
        assertNotNull(context.get(REGISTRATION_RESPONSE_KEY));
        assertNotNull(context.get(USER_CONTEXT_KEY));
    }

    @Test
    public void must_not_initialize_client_in_user_profile() {
        context.put(CLIENT_CONTEXT_KEY, null);

        RegistrationResponse registrationResponse = new RegistrationResponse();
        registrationResponse.setUser(new User());
        when(userService.register(any(), any(), any(), any())).thenReturn(Single.just(registrationResponse));

        registerProcessHandler.handle(context);

        verify(userService).register(any(), argThat(user -> user.getClient() == null), any(), any());
        context.verifyNext(1);
        assertNotNull(context.get(REGISTRATION_RESPONSE_KEY));
        assertNotNull(context.get(USER_CONTEXT_KEY));
    }
}
