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

package io.gravitee.am.gateway.handler.root.resources.endpoint.identifierfirst;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.root.resources.handler.client.ClientRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSelectionRuleHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
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
import java.util.TreeSet;
import java.util.UUID;

import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_PROVIDER_CONTEXT_KEY;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class IdentifierFirstLoginEndpointTest extends RxWebTestBase {

    public final LoginSelectionRuleHandler loginSelectionRuleHandler = new LoginSelectionRuleHandler(true);

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private BotDetectionManager botDetectionManager;

    private Client appClient;
    private io.gravitee.am.gateway.handler.root.resources.endpoint.identifierfirst.IdentifierFirstLoginEndpoint identifierFirstLoginEndpoint;
    private ClientRequestParseHandler clientRequestParseHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        clientRequestParseHandler = new ClientRequestParseHandler(clientSyncService);
        clientRequestParseHandler.setRequired(true);

        final LoginSettings loginSettings = new LoginSettings();
        loginSettings.setIdentifierFirstEnabled(true);

        appClient = new Client();
        appClient.setClientId(UUID.randomUUID().toString());

        domain = new Domain();
        domain.setId(UUID.randomUUID().toString());
        domain.setPath("/id-first-domain");
        domain.setLoginSettings(loginSettings);

        appClient.setDomain(domain.getId());
        when(clientSyncService.findByClientId(appClient.getClientId())).thenReturn(Maybe.just(appClient));

        identifierFirstLoginEndpoint = new IdentifierFirstLoginEndpoint(templateEngine, domain, botDetectionManager);

        router.route("/login/identifier")
                .handler(clientRequestParseHandler)
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void mustInvokeIdentifierFirstLoginEndpoint() throws Exception {
        router.route(HttpMethod.GET, "/login/identifier").handler(get200AssertMockRoutingContextHandler(identifierFirstLoginEndpoint));
        testRequest(
                HttpMethod.GET, "/login/identifier?client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void mustNotInvokeIdentifierFirstLoginEndpoint_noClientId() throws Exception {
        testRequest(
                HttpMethod.GET, "/login/identifier",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void mustNotInvokeIdentifierFirstLoginEndpoint_wrongClientId() throws Exception {
        when(clientSyncService.findByClientId(appClient.getClientId())).thenReturn(Maybe.empty());
        testRequest(
                HttpMethod.GET, "/login/identifier?client_id=" + appClient.getClientId(),
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldInvokeLoginEndpoint_noRedirectNoMatchingProviders() throws Exception {
        router.route(HttpMethod.POST, "/login/identifier")
                .handler(routingContext -> {
                    final IdentityProvider idp = new IdentityProvider();
                    idp.setId("provider-id");
                    routingContext.put(SOCIAL_PROVIDER_CONTEXT_KEY, List.of(idp));
                    routingContext.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, Map.of(idp.getId(), "/some/provider/oauth/authorize"));

                    var identityProviders = new TreeSet<ApplicationIdentityProvider>();
                    var appIdp = new ApplicationIdentityProvider();
                    appIdp.setIdentity(idp.getId());
                    appIdp.setSelectionRule("{#context.request['username'].endsWith('other-domain.com')}");
                    identityProviders.add(appIdp);

                    appClient.setIdentityProviders(identityProviders);

                    routingContext.next();
                })
                .handler(clientRequestParseHandler::handle)
                .handler(loginSelectionRuleHandler)
                .handler(identifierFirstLoginEndpoint::handle);

        testRequest(
                HttpMethod.POST, "/login/identifier?username=username@domain.com&client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/login?"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLoginEndpoint_noRedirectNoMatchingProviders_appIdpRuleIsNull() throws Exception {
        router.route(HttpMethod.POST, "/login/identifier")
                .handler(routingContext -> {
                    final IdentityProvider idp = new IdentityProvider();
                    idp.setId("provider-id");
                    routingContext.put(SOCIAL_PROVIDER_CONTEXT_KEY, List.of(idp));
                    routingContext.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, Map.of(idp.getId(), "/some/provider/oauth/authorize"));

                    var identityProviders = new TreeSet<ApplicationIdentityProvider>();
                    var appIdp = new ApplicationIdentityProvider();
                    appIdp.setIdentity(idp.getId());
                    identityProviders.add(appIdp);

                    appClient.setIdentityProviders(identityProviders);

                    routingContext.next();
                })
                .handler(clientRequestParseHandler::handle)
                .handler(loginSelectionRuleHandler)
                .handler(identifierFirstLoginEndpoint::handle);

        testRequest(
                HttpMethod.POST, "/login/identifier?username=username@domain.com&client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/login?"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLoginEndpoint_noRedirectUsernameIsNotAnEmail() throws Exception {
        router.route(HttpMethod.POST, "/login/identifier")
                .handler(routingContext -> {
                    final IdentityProvider idp = new IdentityProvider();
                    idp.setId("provider-id");
                    routingContext.put(SOCIAL_PROVIDER_CONTEXT_KEY, List.of(idp));
                    routingContext.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, Map.of(idp.getId(), "/some/provider/oauth/authorize"));

                    var identityProviders = new TreeSet<ApplicationIdentityProvider>();
                    var appIdp = new ApplicationIdentityProvider();
                    appIdp.setIdentity(idp.getId());
                    identityProviders.add(appIdp);

                    appClient.setIdentityProviders(identityProviders);
                    routingContext.next();
                })
                .handler(clientRequestParseHandler::handle)
                .handler(loginSelectionRuleHandler)
                .handler(identifierFirstLoginEndpoint::handle);

        testRequest(
                HttpMethod.POST, "/login/identifier?username=username&client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/login?"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLoginEndpoint_noRedirectNoSocialIdpsInContext() throws Exception {
        router.route(HttpMethod.POST, "/login/identifier")
                .handler(clientRequestParseHandler::handle)
                .handler(loginSelectionRuleHandler)
                .handler(identifierFirstLoginEndpoint::handle);

        testRequest(
                HttpMethod.POST, "/login/identifier?username=username@domain.com&client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/login?"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLoginEndpoint_noRedirectClientHasNoIdps() throws Exception {
        router.route(HttpMethod.POST, "/login/identifier")
                .handler(routingContext -> {
                    final IdentityProvider idp = new IdentityProvider();
                    idp.setId("provider-id");
                    routingContext.put(SOCIAL_PROVIDER_CONTEXT_KEY, List.of(idp));
                    routingContext.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, Map.of(idp.getId(), "/some/provider/oauth/authorize"));

                    routingContext.next();
                })
                .handler(clientRequestParseHandler::handle)
                .handler(loginSelectionRuleHandler)
                .handler(identifierFirstLoginEndpoint::handle);

        testRequest(
                HttpMethod.POST, "/login/identifier?username=username@domain.com&client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/login?"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLoginEndpoint_redirectUsernameMatchesRule() throws Exception {
        router.route(HttpMethod.POST, "/login/identifier")
                .handler(routingContext -> {
                    final IdentityProvider idp = new IdentityProvider();
                    idp.setId("provider-id");
                    routingContext.put(SOCIAL_PROVIDER_CONTEXT_KEY, List.of(idp));
                    routingContext.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, Map.of(idp.getId(), "https://host/some/provider/oauth/authorize"));

                    var identityProviders = new TreeSet<ApplicationIdentityProvider>();
                    var appIdp = new ApplicationIdentityProvider();
                    appIdp.setIdentity(idp.getId());
                    appIdp.setSelectionRule("{#request.params['username'][0] matches '.+@domain.com$'}");
                    identityProviders.add(appIdp);

                    appClient.setIdentityProviders(identityProviders);

                    routingContext.next();
                })
                .handler(clientRequestParseHandler::handle)
                .handler(loginSelectionRuleHandler)
                .handler(identifierFirstLoginEndpoint::handle);

        testRequest(
                HttpMethod.POST, "/login/identifier?username=username@domain.com&client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://host/some/provider/oauth/authorize?login_hint=username%40domain.com"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLoginEndpoint_redirectUsernameMatchesDomainAndSpecificIdp() throws Exception {
        router.route(HttpMethod.POST, "/login/identifier")
                .handler(routingContext -> {
                    final IdentityProvider idp = new IdentityProvider();
                    idp.setId("provider-id");
                    idp.setType("google");
                    routingContext.put(SOCIAL_PROVIDER_CONTEXT_KEY, List.of(idp));
                    routingContext.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, Map.of(idp.getId(), "https://host/some/provider/oauth/authorize"));

                    var identityProviders = new TreeSet<ApplicationIdentityProvider>();
                    var appIdp = new ApplicationIdentityProvider();
                    appIdp.setIdentity(idp.getId());
                    appIdp.setSelectionRule("{#request.params['username'][0] matches '.+@domain.com$'}");
                    identityProviders.add(appIdp);
                    appClient.setIdentityProviders(identityProviders);
                    routingContext.next();
                })
                .handler(clientRequestParseHandler::handle)
                .handler(loginSelectionRuleHandler)
                .handler(identifierFirstLoginEndpoint::handle);

        testRequest(
                HttpMethod.POST, "/login/identifier?username=username@domain.com&client_id=" + appClient.getClientId() + "&response_type=code&redirect_uri=somewhere.com",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://host/some/provider/oauth/authorize?login_hint=username%40domain.com"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    private Handler<RoutingContext> get200AssertMockRoutingContextHandler(IdentifierFirstLoginEndpoint identifierFirstLoginEndpoint) {
        return routingContext -> {
            doAnswer(answer -> {
                try {
                    assertNotNull(routingContext.get(CLIENT_CONTEXT_KEY));
                    assertEquals(routingContext.get(DOMAIN_CONTEXT_KEY), domain);
                    assertNotNull(routingContext.get(PARAM_CONTEXT_KEY));
                    final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
                    assertEquals(routingContext.get(ACTION_KEY), resolveProxyRequest(routingContext.request(),
                            routingContext.get(CONTEXT_PATH) + "/login/identifier", queryParams, true));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    routingContext.end();
                }
                return answer;
            }).when(templateEngine).render(Mockito.<Map<String, Object>>any(), Mockito.any(), Mockito.any());

            identifierFirstLoginEndpoint.handle(routingContext);
        };
    }
}
