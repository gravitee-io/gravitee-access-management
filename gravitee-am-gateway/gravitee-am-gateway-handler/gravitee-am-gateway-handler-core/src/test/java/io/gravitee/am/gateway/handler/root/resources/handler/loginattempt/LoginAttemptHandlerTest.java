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

package io.gravitee.am.gateway.handler.root.resources.handler.loginattempt;

import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.LoginAttemptService;
import io.reactivex.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Set;
import java.util.UUID;

import static io.gravitee.am.common.utils.ConstantKeys.USERNAME_PARAM_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginAttemptHandlerTest {

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private LoginAttemptService loginAttemptService;
    private Domain domain;
    private Client client;
    private LoginAttemptHandler loginAttemptHandler;
    private SpyRoutingContext spyRoutingContext;

    @Before
    public void setUp() {

        final IdentityProvider internal = new IdentityProvider();
        internal.setId(UUID.randomUUID().toString());
        internal.setExternal(false);

        final IdentityProvider external = new IdentityProvider();
        external.setId(UUID.randomUUID().toString());
        external.setExternal(true);

        doReturn(internal).when(identityProviderManager).getIdentityProvider(internal.getId());

        domain = new Domain();
        domain.setId(UUID.randomUUID().toString());
        client = new Client();
        client.setId(UUID.randomUUID().toString());
        client.setClientId(UUID.randomUUID().toString());
        client.setIdentities(Set.of(internal.getId(), external.getId()));

        final LoginAttempt attempts = new LoginAttempt();
        attempts.setAttempts(5);
        doReturn(Maybe.just(attempts)).when(loginAttemptService).checkAccount(any(), any());

        spyRoutingContext = spy(new SpyRoutingContext());
        loginAttemptHandler = new LoginAttemptHandler(domain, identityProviderManager, loginAttemptService);
        doNothing().when(spyRoutingContext).next();
    }

    @Test
    public void mustDoNextNoLoginAttemptApplied_noClient() {
        loginAttemptHandler.handle(spyRoutingContext);
        verify(spyRoutingContext, times(1)).next();
    }

    @Test
    public void mustDoNextNoLoginAttemptApplied_noAdaptiveRule() {
        spyRoutingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        loginAttemptHandler.handle(spyRoutingContext);
        verify(spyRoutingContext, times(1)).next();
    }

    @Test
    public void mustDoNextNoLoginAttemptApplied_noUsername() {
        spyRoutingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

        final MFASettings mfaSettings = new MFASettings();
        mfaSettings.setAdaptiveAuthenticationRule("{#variable == true}");
        client.setMfaSettings(mfaSettings);

        loginAttemptHandler.handle(spyRoutingContext);
        verify(spyRoutingContext, times(1)).next();
    }

    @Test
    public void mustTryLoginAttemptApplied() throws InterruptedException {
        spyRoutingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
        spyRoutingContext.putParam(USERNAME_PARAM_KEY, "username");

        final MFASettings mfaSettings = new MFASettings();
        mfaSettings.setAdaptiveAuthenticationRule("{#variable == true}");
        client.setMfaSettings(mfaSettings);

        loginAttemptHandler.handle(spyRoutingContext);
        //Necessary so the reactive consumer is consumed
        Thread.sleep(1000);
        verify(spyRoutingContext, times(1)).next();
    }
}
