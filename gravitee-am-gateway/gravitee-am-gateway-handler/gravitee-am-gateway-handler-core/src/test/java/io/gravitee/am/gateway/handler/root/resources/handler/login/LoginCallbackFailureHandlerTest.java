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

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.UserAuthenticationAbortedException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.CloseSessionMode;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.MultiMap;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.common.utils.ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.PARAM_CONTEXT_KEY;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private JWTService jwtService;

    @Mock
    private CertificateManager certificateManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/login/callback")
                .handler(rc -> rc.fail(new PolicyChainException("policy_exception")))
                .failureHandler(new LoginCallbackFailureHandler(domain, authenticationFlowContextService, identityProviderManager, jwtService, certificateManager));
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
    public void shouldNotRedirectToLoginPage_onPolicyError__whenSocialIdp_not_preserve_sessions() throws Exception {
        var authenticationProvider = mock(SocialAuthenticationProvider.class);
        when(authenticationProvider.closeSessionAfterSignIn()).thenReturn(CloseSessionMode.REDIRECT);
        var signOutRequest = new Request();
        signOutRequest.setUri("http://idp/signout");
        when(authenticationProvider.signOutUrl(any())).thenReturn(Maybe.just(signOutRequest));

        when(certificateManager.defaultCertificateProvider()).thenReturn(mock(CertificateProvider.class));
        when(jwtService.encode(any(JWT.class), any(CertificateProvider.class))).thenReturn(Single.just("failure_state"));

        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_CONTEXT_KEY, authenticationProvider);
            routingContext.put(OIDC_PROVIDER_ID_TOKEN_KEY, "op_id_token_value");
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/login/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.startsWith("http://idp/signout"));
                    assertTrue(location.contains("state=failure_state"));
                    assertTrue(location.contains("id_token_hint=op_id_token_value"));
                    assertTrue(location.contains("post_logout_redirect_uri="));
                    assertTrue(location.contains("%2Flogin%2Fcallback"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToLoginPage_onPolicyError__whenSocialIdp_not_preserve_sessions_But_StateProcessingFails() throws Exception {
        var authenticationProvider = mock(SocialAuthenticationProvider.class);
        when(authenticationProvider.closeSessionAfterSignIn()).thenReturn(CloseSessionMode.REDIRECT);
        var signOutRequest = new Request();
        signOutRequest.setUri("http://idp/signout");
        when(authenticationProvider.signOutUrl(any())).thenReturn(Maybe.just(signOutRequest));

        when(certificateManager.defaultCertificateProvider()).thenReturn(mock(CertificateProvider.class));
        when(jwtService.encode(any(JWT.class), any(CertificateProvider.class))).thenReturn(Single.error(new IllegalArgumentException()));

        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_CONTEXT_KEY, authenticationProvider);
            routingContext.put(OIDC_PROVIDER_ID_TOKEN_KEY, "op_id_token_value");
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
    public void shouldRedirectToLoginPage_without_error_when_user_abort_auth_on_social_idp() throws Exception {
        var authenticationProvider = mock(SocialAuthenticationProvider.class);

        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_CONTEXT_KEY, authenticationProvider);
            routingContext.fail(new UserAuthenticationAbortedException("access_denied", "User auth aborted"));
        });

        testRequest(
                HttpMethod.GET, "/login/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertFalse(location.contains("error="));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToLoginPage_with_error_when_social_idp_throw_an_error() throws Exception {
        var authenticationProvider = mock(SocialAuthenticationProvider.class);

        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_CONTEXT_KEY, authenticationProvider);
            routingContext.fail(new BadCredentialsException("access_denied"));
        });

        testRequest(
                HttpMethod.GET, "/login/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/login?error=social_authentication_failed"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToLoginPage_hideLoginForm_multipleIdP() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            client.setIdentityProviders(new TreeSet<>(Stream.of("idp1", "idp2", "idp3").map(idp -> {
                final ApplicationIdentityProvider applicationIdentityProvider = new ApplicationIdentityProvider();
                applicationIdentityProvider.setIdentity(idp);
                return applicationIdentityProvider;
            }).collect(Collectors.toSet())));
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
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
                    assertTrue(location.contains("/login?error=social_authentication_failed&error_description=policy_exception"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToSPRedirectUri_hideLogin_oneExternalIdP_codeFlow() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            client.setIdentityProviders(new TreeSet<>(Stream.of("idp1").map(idp -> {
                final ApplicationIdentityProvider applicationIdentityProvider = new ApplicationIdentityProvider();
                applicationIdentityProvider.setIdentity(idp);
                return applicationIdentityProvider;
            }).collect(Collectors.toSet())));
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
                    Assertions.assertThat(location)
                            .isNotNull()
                            .isEqualTo("https://sp-app/callback?error=server_error&error_description=policy_exception&state=12345");
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToSPRedirectUri_SelectionRule_oneExternalIdP_codeFlow() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            client.setIdentityProviders(new TreeSet<>(Stream.of("idp1").map(idp -> {
                final ApplicationIdentityProvider applicationIdentityProvider = new ApplicationIdentityProvider();
                applicationIdentityProvider.setIdentity(idp);
                applicationIdentityProvider.setSelectionRule("true");
                return applicationIdentityProvider;
            }).collect(Collectors.toSet())));
            LoginSettings loginSettings = new LoginSettings();
            loginSettings.setInherited(false);
            loginSettings.setHideForm(false);
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
            client.setIdentityProviders(new TreeSet<>(Stream.of("idp1").map(idp -> {
                final ApplicationIdentityProvider applicationIdentityProvider = new ApplicationIdentityProvider();
                applicationIdentityProvider.setIdentity(idp);
                return applicationIdentityProvider;
            }).collect(Collectors.toSet())));
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

    @Test
    public void shouldRedirectToSPRedirectUri_SelectionRule_oneExternalIdP_implicitFlow() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            client.setIdentityProviders(new TreeSet<>(Stream.of("idp1").map(idp -> {
                final ApplicationIdentityProvider applicationIdentityProvider = new ApplicationIdentityProvider();
                applicationIdentityProvider.setIdentity(idp);
                applicationIdentityProvider.setSelectionRule("true");
                return applicationIdentityProvider;
            }).collect(Collectors.toSet())));
            LoginSettings loginSettings = new LoginSettings();
            loginSettings.setInherited(false);
            loginSettings.setHideForm(false);
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
