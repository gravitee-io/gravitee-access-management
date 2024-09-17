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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.rxjava3.core.MultiMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.TreeSet;

import static io.gravitee.am.common.utils.ConstantKeys.PARAM_CONTEXT_KEY;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LoginFailureHandlerTest extends RxWebTestBase {

    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Mock
    private Domain domain;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private PolicyChainException policyChainException;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/login")
                .handler(rc -> rc.fail(policyChainException))
                .failureHandler(new LoginFailureHandler(authenticationFlowContextService, domain, identityProviderManager));
    }

    @Test
    public void shouldRedirectToLoginCallbackUrl_loginFormHidden_OneIdp() throws Exception {
        LoginSettings loginSettings = new LoginSettings();
        loginSettings.setInherited(false);
        loginSettings.setHideForm(true);
        when(domain.getLoginSettings()).thenReturn(loginSettings);

        IdentityProvider idp = mock(IdentityProvider.class);
        when(idp.isExternal()).thenReturn(true);
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(idp);

        Client mockClient = mock(Client.class);
        final ApplicationIdentityProvider applicationIdentityProvider = new ApplicationIdentityProvider();
        TreeSet<ApplicationIdentityProvider> idps = new TreeSet<>();
        idps.add(applicationIdentityProvider);
        applicationIdentityProvider.setIdentity("idp");
        when(mockClient.getIdentityProviders()).thenReturn(idps);


        MultiMap multiMap = new MultiMap(new HeadersMultiMap());
        multiMap.add("redirect_uri", "http://myhost/login/callback");

        when(policyChainException.key()).thenReturn("CALLOUT_EXIT_ON_ERROR");
        when(policyChainException.getMessage()).thenReturn("{\"errorTest\": \"Test Error\"}");


        router.route().order(-1).handler(routingContext -> {
            Client client = mockClient;
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(PARAM_CONTEXT_KEY, multiMap);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("http://myhost/login/callback?error=login_failed&error_code=CALLOUT_EXIT_ON_ERROR&error_description=%7B%22errorTest%22%3A+%22Test+Error%22%7D"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToAM_loginFormNotHidden_OneIdp() throws Exception {
        LoginSettings loginSettings = new LoginSettings();
        loginSettings.setInherited(false);
        loginSettings.setHideForm(false);
        when(domain.getLoginSettings()).thenReturn(loginSettings);

        IdentityProvider idp = mock(IdentityProvider.class);
        when(idp.isExternal()).thenReturn(true);
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(idp);

        Client mockClient = mock(Client.class);
        final ApplicationIdentityProvider applicationIdentityProvider = new ApplicationIdentityProvider();
        TreeSet<ApplicationIdentityProvider> idps = new TreeSet<>();
        idps.add(applicationIdentityProvider);
        applicationIdentityProvider.setIdentity("idp");
        when(mockClient.getIdentityProviders()).thenReturn(idps);


        MultiMap multiMap = new MultiMap(new HeadersMultiMap());
        multiMap.add("redirect_uri", "http://myhost/login/callback");

        when(policyChainException.key()).thenReturn("CALLOUT_EXIT_ON_ERROR");
        when(policyChainException.getMessage()).thenReturn("{\"errorTest\": \"Test Error\"}");


        router.route().order(-1).handler(routingContext -> {
            Client client = mockClient;
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(PARAM_CONTEXT_KEY, multiMap);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/login?error=login_failed&error_code=CALLOUT_EXIT_ON_ERROR&error_description=%7B%22errorTest%22%3A+%22Test+Error%22%7D"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
