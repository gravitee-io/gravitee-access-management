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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.MultiMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashSet;

import static io.gravitee.am.common.utils.ConstantKeys.PARAM_CONTEXT_KEY;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginCallbackFailureHandlerTest extends RxWebTestBase {

    @Mock
    private Domain domain;

    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/login/callback")
                .handler(rc -> rc.fail(new PolicyChainException("policy_exception")))
                .failureHandler(new LoginCallbackFailureHandler(domain, authenticationFlowContextService, identityProviderManager));
    }

    @Test
    public void shouldRedirectToLoginPage_nominalCase() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/login/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/login?error=social_authentication_failed&error_description=policy_exception"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToLoginPage_hideLoginForm_multipleIdP() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            client.setIdentities(new HashSet<>(Arrays.asList("idp1", "idp2", "idp3")));
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        IdentityProvider idp = mock(IdentityProvider.class);
        when(idp.isExternal()).thenReturn(false);
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(idp);

        testRequest(
                HttpMethod.GET, "/login/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/login?error=social_authentication_failed&error_description=policy_exception"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToSPRedirectUri_hideLogin_oneExternalIdP_codeFlow() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            client.setIdentities(new HashSet<>(Arrays.asList("idp1")));
            LoginSettings loginSettings = new LoginSettings();
            loginSettings.setInherited(false);
            loginSettings.setHideForm(true);
            client.setLoginSettings(loginSettings);
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

            // original parameters
            final MultiMap originalParams = MultiMap.caseInsensitiveMultiMap();
            originalParams.set(Parameters.REDIRECT_URI, "https://sp-app/callback");
            originalParams.set(Parameters.RESPONSE_TYPE, ResponseType.CODE);
            originalParams.set(Parameters.STATE, "12345");
            routingContext.put(PARAM_CONTEXT_KEY, originalParams);
            routingContext.next();
        });

        IdentityProvider idp = mock(IdentityProvider.class);
        when(idp.isExternal()).thenReturn(true);
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(idp);

        testRequest(
                HttpMethod.GET, "/login/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://sp-app/callback?error=server_error&error_description=policy_exception&state=12345"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToSPRedirectUri_hideLogin_oneExternalIdP_implicitFlow() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            client.setIdentities(new HashSet<>(Arrays.asList("idp1")));
            LoginSettings loginSettings = new LoginSettings();
            loginSettings.setInherited(false);
            loginSettings.setHideForm(true);
            client.setLoginSettings(loginSettings);
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);

            // original parameters
            final MultiMap originalParams = MultiMap.caseInsensitiveMultiMap();
            originalParams.set(Parameters.REDIRECT_URI, "https://sp-app/callback");
            originalParams.set(Parameters.RESPONSE_TYPE, ResponseType.TOKEN);
            originalParams.set(Parameters.STATE, "12345");
            routingContext.put(PARAM_CONTEXT_KEY, originalParams);
            routingContext.next();
        });

        IdentityProvider idp = mock(IdentityProvider.class);
        when(idp.isExternal()).thenReturn(true);
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(idp);

        testRequest(
                HttpMethod.GET, "/login/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://sp-app/callback#error=server_error&error_description=policy_exception&state=12345"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
