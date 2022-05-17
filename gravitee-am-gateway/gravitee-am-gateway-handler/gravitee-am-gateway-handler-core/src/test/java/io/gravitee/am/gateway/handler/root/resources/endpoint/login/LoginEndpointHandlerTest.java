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
package io.gravitee.am.gateway.handler.root.resources.endpoint.login;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.root.resources.handler.client.ClientRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginHideFormHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_PROVIDER_CONTEXT_KEY;
import static java.lang.Boolean.TRUE;
import static java.time.temporal.ChronoUnit.DECADES;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginEndpointHandlerTest extends RxWebTestBase {

    private static final String FORGOT_ACTION_KEY = "forgotPasswordAction";
    private static final String REGISTER_ACTION_KEY = "registerAction";
    private static final String WEBAUTHN_ACTION_KEY = "passwordlessAction";
    private static final String ALLOW_FORGOT_PASSWORD_CONTEXT_KEY = "allowForgotPassword";
    private static final String ALLOW_REGISTER_CONTEXT_KEY = "allowRegister";
    private static final String ALLOW_PASSWORDLESS_CONTEXT_KEY = "allowPasswordless";
    private static final String HIDE_FORM_CONTEXT_KEY = "hideLoginForm";
    private static final String IDENTIFIER_FIRST_LOGIN_ENABLED = "identifierFirstLoginEnabled";
    private static final String BACK_TO_LOGIN_IDENTIFIER_ACTION_KEY = "backToLoginIdentifierAction";

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private BotDetectionManager botDetectionManager;

    @Mock
    private DeviceIdentifierManager deviceIdentifierManager;

    @Mock
    private UserActivityService userActivityService;

    @Mock
    private ClientSyncService clientSyncService;
    private Client appClient;
    private LoginEndpoint loginEndpoint;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        ClientRequestParseHandler clientRequestParseHandler = new ClientRequestParseHandler(clientSyncService);
        clientRequestParseHandler.setRequired(true);

        final LoginSettings loginSettings = new LoginSettings();

        appClient = new Client();
        appClient.setClientId(UUID.randomUUID().toString());


        domain = new Domain();
        domain.setId(UUID.randomUUID().toString());
        domain.setPath("/login-domain");
        domain.setLoginSettings(loginSettings);
        appClient.setDomain(domain.getId());
        loginEndpoint = new LoginEndpoint(templateEngine, domain, botDetectionManager, deviceIdentifierManager, userActivityService);
        appClient.setLoginSettings(loginSettings);
        doReturn(Map.of()).when(deviceIdentifierManager).getTemplateVariables(any());
        doReturn(true).when(userActivityService).canSaveUserActivity();
        doReturn(3L).when(userActivityService).getRetentionTime();
        doReturn(DECADES).when(userActivityService).getRetentionUnit();

        router.route(HttpMethod.GET, "/login")
                .handler(routingContext -> {
                    routingContext.put(CONTEXT_PATH, "");
                    routingContext.next();
                })
                .handler(clientRequestParseHandler)
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldInvokeLoginEndpoint_buildTemplate() throws Exception {
        appClient.getLoginSettings().setIdentifierFirstEnabled(false);
        appClient.getLoginSettings().setHideForm(false);
        router.route(HttpMethod.GET, "/login").handler(get200AssertMockRoutingContextHandler(loginEndpoint, false, false));
        when(clientSyncService.findByClientId(appClient.getClientId())).thenReturn(Maybe.just(appClient));
        testRequest(
                HttpMethod.GET, "/login?client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldInvokeLoginEndpoint_redirectProvider() throws Exception {
        appClient.getLoginSettings().setIdentifierFirstEnabled(false);
        appClient.getLoginSettings().setHideForm(TRUE);
        router.route(HttpMethod.GET, "/login")
                .handler(routingContext -> {
                    final IdentityProvider idp = new IdentityProvider();
                    idp.setId("provider-id");
                    routingContext.put(SOCIAL_PROVIDER_CONTEXT_KEY, List.of(idp));
                    routingContext.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, Map.of(idp.getId(), "/some/provider/oauth/authorize"));
                    routingContext.next();
                })
                .handler(new LoginHideFormHandler(domain))
                .handler(get302AssertMockRoutingContextHandler(loginEndpoint, true, false));
        when(clientSyncService.findByClientId(appClient.getClientId())).thenReturn(Maybe.just(appClient));
        testRequest(
                HttpMethod.GET, "/login?client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                HttpStatusCode.FOUND_302, "Found");
    }

    @Test
    public void shouldInvokeLoginEndpoint_noRedirect() throws Exception {
        appClient.getLoginSettings().setHideForm(false);
        router.route(HttpMethod.GET, "/login").handler(routingContext -> {
            final IdentityProvider idp = new IdentityProvider();
            idp.setId("provider-id");
            routingContext.put(SOCIAL_PROVIDER_CONTEXT_KEY, List.of(idp));
            routingContext.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, Map.of(idp.getId(), "/some/provider/oauth/authorize"));
            routingContext.next();
        }).handler(get200AssertMockRoutingContextHandler(loginEndpoint, false, false));
        when(clientSyncService.findByClientId(appClient.getClientId())).thenReturn(Maybe.just(appClient));
        testRequest(
                HttpMethod.GET, "/login?client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldInvokeLoginEndpoint_noRedirectMultipleProviders() throws Exception {
        appClient.getLoginSettings().setIdentifierFirstEnabled(false);
        appClient.getLoginSettings().setHideForm(TRUE);
        router.route(HttpMethod.GET, "/login")
                .handler(routingContext -> {
                    final IdentityProvider idp1 = new IdentityProvider();
                    idp1.setId("provider-id-1");
                    final IdentityProvider idp2 = new IdentityProvider();
                    idp2.setId("provider-id-2");
                    routingContext.put(SOCIAL_PROVIDER_CONTEXT_KEY, List.of(idp1, idp2));
                    routingContext.next();
                })
                .handler(new LoginHideFormHandler(domain))
                .handler(get200AssertMockRoutingContextHandler(loginEndpoint, true, false));
        when(clientSyncService.findByClientId(appClient.getClientId())).thenReturn(Maybe.just(appClient));
        testRequest(
                HttpMethod.GET, "/login?client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldNotInvokeLoginEndpoint_emptyUsernameWhenIdentifierFirstEnabled() throws Exception {
        appClient.getLoginSettings().setIdentifierFirstEnabled(true);
        router.route(HttpMethod.GET, "/login").handler(context -> {
            loginEndpoint.handle(context);
            context.next();
        });
        ;
        when(clientSyncService.findByClientId(appClient.getClientId())).thenReturn(Maybe.just(appClient));
        testRequest(
                HttpMethod.GET, "/login?client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com&username=",
                HttpStatusCode.FOUND_302, "Found");
    }

    @Test
    public void shouldNotInvokeLoginEndpoint_nullUsernameWhenIdentifierFirstEnabled() throws Exception {
        appClient.getLoginSettings().setIdentifierFirstEnabled(true);
        router.route(HttpMethod.GET, "/login").handler(context -> {
            loginEndpoint.handle(context);
            context.next();
        });
        when(clientSyncService.findByClientId(appClient.getClientId())).thenReturn(Maybe.just(appClient));
        testRequest(
                HttpMethod.GET, "/login?client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                HttpStatusCode.FOUND_302, "Found");
    }

    @Test
    public void shouldNotInvokeLoginEndpoint_noClientParameter() throws Exception {
        testRequest(
                HttpMethod.GET, "/login",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldNotInvokeLoginEndpoint_noClient() throws Exception {
        when(clientSyncService.findByClientId("test")).thenReturn(Maybe.empty());

        testRequest(
                HttpMethod.GET, "/login?client_id=test",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    private Handler<RoutingContext> get200AssertMockRoutingContextHandler(LoginEndpoint loginEndpoint, boolean expectedHide, boolean expectedFirstIdentifier) {
        return routingContext -> {
            doAnswer(answer -> {
                try {
                    assertNotNull(routingContext.get(CLIENT_CONTEXT_KEY));
                    assertEquals(routingContext.get(DOMAIN_CONTEXT_KEY), domain);
                    assertNotNull(routingContext.get(PARAM_CONTEXT_KEY));

                    final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
                    var queryParamsNoUsername = MultiMap.caseInsensitiveMultiMap();
                    queryParamsNoUsername.addAll(queryParams);
                    queryParamsNoUsername.remove(USERNAME_PARAM_KEY);
                    assertEquals(routingContext.get(ACTION_KEY), resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/login", queryParams, true));
                    assertEquals(routingContext.get(FORGOT_ACTION_KEY), resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/forgotPassword", queryParams, true));
                    assertEquals(routingContext.get(REGISTER_ACTION_KEY), resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/register", queryParams, true));
                    assertEquals(routingContext.get(WEBAUTHN_ACTION_KEY), resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/webauthn/login", queryParams, true));
                    if (expectedFirstIdentifier) {
                        assertEquals(routingContext.get(BACK_TO_LOGIN_IDENTIFIER_ACTION_KEY), resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/login/identifier", queryParamsNoUsername, true));
                    }
                    assertFalse(TRUE.equals(routingContext.get(ALLOW_FORGOT_PASSWORD_CONTEXT_KEY)));
                    assertFalse(TRUE.equals(routingContext.get(ALLOW_REGISTER_CONTEXT_KEY)));
                    assertFalse(TRUE.equals(routingContext.get(ALLOW_PASSWORDLESS_CONTEXT_KEY)));
                    assertEquals(TRUE.equals(routingContext.get(HIDE_FORM_CONTEXT_KEY)), expectedHide);
                    assertEquals(TRUE.equals(routingContext.get(IDENTIFIER_FIRST_LOGIN_ENABLED)), expectedFirstIdentifier);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    routingContext.end();
                }
                return answer;
            }).when(templateEngine).render(Mockito.<Map<String, Object>>any(), Mockito.any(), Mockito.any());
            loginEndpoint.handle(routingContext);
        };
    }

    private Handler<RoutingContext> get302AssertMockRoutingContextHandler(LoginEndpoint loginEndpoint, boolean expectedHide, boolean expectedFirstIdentifier) {
        return routingContext -> {
            loginEndpoint.handle(routingContext);
            try {
                assertNotNull(routingContext.get(CLIENT_CONTEXT_KEY));
                assertEquals(routingContext.get(DOMAIN_CONTEXT_KEY), domain);
                assertNotNull(routingContext.get(PARAM_CONTEXT_KEY));

                final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
                var queryParamsNoUsername = MultiMap.caseInsensitiveMultiMap();
                queryParamsNoUsername.addAll(queryParams);
                queryParamsNoUsername.remove(USERNAME_PARAM_KEY);

                assertEquals(routingContext.get(ACTION_KEY), resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/login", queryParams, true));
                assertEquals(routingContext.get(FORGOT_ACTION_KEY), resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/forgotPassword", queryParams, true));
                assertEquals(routingContext.get(REGISTER_ACTION_KEY), resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/register", queryParams, true));
                assertEquals(routingContext.get(WEBAUTHN_ACTION_KEY), resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH) + "/webauthn/login", queryParams, true));

                assertFalse(TRUE.equals(routingContext.get(ALLOW_FORGOT_PASSWORD_CONTEXT_KEY)));
                assertFalse(TRUE.equals(routingContext.get(ALLOW_REGISTER_CONTEXT_KEY)));
                assertFalse(TRUE.equals(routingContext.get(ALLOW_PASSWORDLESS_CONTEXT_KEY)));
                assertEquals(TRUE.equals(routingContext.get(HIDE_FORM_CONTEXT_KEY)), expectedHide);
                assertEquals(TRUE.equals(routingContext.get(IDENTIFIER_FIRST_LOGIN_ENABLED)), expectedFirstIdentifier);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                routingContext.end();
            }
        };
    }
}
