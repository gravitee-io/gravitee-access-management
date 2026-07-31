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
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.SessionStore;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LoginAuthenticationHandlerTest extends RxWebTestBase {

    public static final int CONTEXT_SETUP = -1;

    @Mock
    private IdentityProviderManager identityProviderManager;
    @Mock
    private JWTService jwtService;
    @Mock
    private CertificateManager certificateManager;
    @Mock
    private Client client;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        router.get(RootProvider.PATH_LOGIN)
                .handler(SessionHandler.create(SessionStore.create(vertx)))
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                })
                .handler(new LoginAuthenticationHandler(identityProviderManager, jwtService, certificateManager))
                .handler(checkContextAssertions())
                .handler(rc -> rc.response().setStatusCode(200).send("completed"))
                .failureHandler(c -> c.response()
                        .setStatusCode(500)
                        .send(c.failure().getClass().getSimpleName() + ": " + c.failure().getMessage()));
    }

    @Test
    public void should_not_provide_idp_in_context_if_no_idp_enabled_on_client() throws Exception {
        when(client.getIdentityProviders()).thenReturn(new TreeSet<>());

        router.route(HttpMethod.GET, RootProvider.PATH_LOGIN);

        assertAfterRequest(rc -> Assertions.assertThat(rc.data()).doesNotContainKeys(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY, ConstantKeys.INTERNAL_PROVIDER_CONTEXT_KEY));

        testRequest(HttpMethod.GET, RootProvider.PATH_LOGIN, 200, "OK", "completed");
    }

    @Test
    public void should_provide_internal_idp_in_context() throws Exception {
        var applicationIdentityProvider = new ApplicationIdentityProvider();
        applicationIdentityProvider.setIdentity("idp");
        var identityProvider = new IdentityProvider();
        identityProvider.setId(applicationIdentityProvider.getIdentity());
        identityProvider.setExternal(false);
        identityProvider.setConfiguration(UUID.randomUUID().toString());

        when(client.getIdentityProviders()).thenReturn(new TreeSet<>(Set.of(applicationIdentityProvider)));
        when(identityProviderManager.getIdentityProvider(identityProvider.getId())).thenReturn(identityProvider);

        router.route(HttpMethod.GET, RootProvider.PATH_LOGIN);

        assertAfterRequest(rc -> {
            Assertions.assertThat(rc.data()).doesNotContainKeys(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY);
            Assertions.assertThat(rc.data()).containsKey(ConstantKeys.INTERNAL_PROVIDER_CONTEXT_KEY);
            List<IdentityProvider> idp = rc.get(ConstantKeys.INTERNAL_PROVIDER_CONTEXT_KEY);
            Assertions.assertThat(idp).hasSize(1);
            Assertions.assertThat(idp.get(0).getConfiguration()).isNull();
        });

        testRequest(HttpMethod.GET, RootProvider.PATH_LOGIN, 200, "OK", "completed");
    }

    @Test
    public void should_provide_social_idp_in_context() throws Exception {
        var applicationIdentityProvider = new ApplicationIdentityProvider();
        applicationIdentityProvider.setIdentity("idp");
        var identityProvider = new IdentityProvider();
        identityProvider.setId(applicationIdentityProvider.getIdentity());
        identityProvider.setExternal(true);
        identityProvider.setConfiguration(UUID.randomUUID().toString());

        when(client.getIdentityProviders()).thenReturn(new TreeSet<>(Set.of(applicationIdentityProvider)));
        when(identityProviderManager.getIdentityProvider(identityProvider.getId())).thenReturn(identityProvider);
        final var authProvider = Mockito.mock(SocialAuthenticationProvider.class);
        final var request = new Request();
        request.setUri(UUID.randomUUID().toString());
        request.setMethod(io.gravitee.common.http.HttpMethod.GET);
        when(authProvider.asyncSignInUrl(any(), any(), any())).thenReturn(Maybe.just(request));
        when(identityProviderManager.get(eq(identityProvider.getId()))).thenReturn(Maybe.just(authProvider));

        router.route(HttpMethod.GET, RootProvider.PATH_LOGIN);

        assertAfterRequest(rc -> {
            Assertions.assertThat(rc.data()).containsKey(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY);
            List<IdentityProvider> idp = rc.get(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY);
            Assertions.assertThat(idp).hasSize(1);
            Assertions.assertThat(idp.get(0).getConfiguration()).isNull();
            Assertions.assertThat(rc.data()).doesNotContainKeys(ConstantKeys.INTERNAL_PROVIDER_CONTEXT_KEY);

            Assertions.assertThat(rc.data()).containsKey(LoginAuthenticationHandler.SOCIAL_PROVIDER_MAP_CONTEXT_KEY);
            Map<String, IdentityProvider> providersById = rc.get(LoginAuthenticationHandler.SOCIAL_PROVIDER_MAP_CONTEXT_KEY);
            Assertions.assertThat(providersById).hasSize(1).containsKey(identityProvider.getId());

            Assertions.assertThat(rc.data()).containsKey(LoginAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY);
            Map<String, String> authorizeUrls = rc.get(LoginAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY);
            Assertions.assertThat(authorizeUrls).hasSize(1).containsKey(identityProvider.getId());
        });

        testRequest(HttpMethod.GET, RootProvider.PATH_LOGIN, 200, "OK", "completed");
    }

    @Test
    public void should_filter_out_social_idp_without_authorize_url_from_all_context_collections() throws Exception {
        var applicationIdentityProviderWithUrl = new ApplicationIdentityProvider();
        applicationIdentityProviderWithUrl.setIdentity("idpWithUrl");
        var identityProviderWithUrl = new IdentityProvider();
        identityProviderWithUrl.setId(applicationIdentityProviderWithUrl.getIdentity());
        identityProviderWithUrl.setExternal(true);
        identityProviderWithUrl.setConfiguration(UUID.randomUUID().toString());

        var applicationIdentityProviderNoUrl = new ApplicationIdentityProvider();
        applicationIdentityProviderNoUrl.setIdentity("idpNoUrl");
        var identityProviderNoUrl = new IdentityProvider();
        identityProviderNoUrl.setId(applicationIdentityProviderNoUrl.getIdentity());
        identityProviderNoUrl.setExternal(true);
        identityProviderNoUrl.setConfiguration(UUID.randomUUID().toString());

        when(client.getIdentityProviders()).thenReturn(new TreeSet<>(Set.of(applicationIdentityProviderWithUrl, applicationIdentityProviderNoUrl)));
        when(identityProviderManager.getIdentityProvider(identityProviderWithUrl.getId())).thenReturn(identityProviderWithUrl);
        when(identityProviderManager.getIdentityProvider(identityProviderNoUrl.getId())).thenReturn(identityProviderNoUrl);

        final var authProviderWithUrl = Mockito.mock(SocialAuthenticationProvider.class);
        final var request = new Request();
        request.setUri(UUID.randomUUID().toString());
        request.setMethod(io.gravitee.common.http.HttpMethod.GET);
        when(authProviderWithUrl.asyncSignInUrl(any(), any(), any())).thenReturn(Maybe.just(request));
        when(identityProviderManager.get(eq(identityProviderWithUrl.getId()))).thenReturn(Maybe.just(authProviderWithUrl));

        final var authProviderNoUrl = Mockito.mock(SocialAuthenticationProvider.class);
        when(authProviderNoUrl.asyncSignInUrl(any(), any(), any())).thenReturn(Maybe.empty());
        when(identityProviderManager.get(eq(identityProviderNoUrl.getId()))).thenReturn(Maybe.just(authProviderNoUrl));

        router.route(HttpMethod.GET, RootProvider.PATH_LOGIN);

        assertAfterRequest(rc -> {
            List<IdentityProvider> idp = rc.get(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY);
            Assertions.assertThat(idp).extracting(IdentityProvider::getId).containsExactly(identityProviderWithUrl.getId());

            Map<String, String> authorizeUrls = rc.get(LoginAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY);
            Assertions.assertThat(authorizeUrls).containsOnlyKeys(identityProviderWithUrl.getId());

            Map<String, IdentityProvider> providersById = rc.get(LoginAuthenticationHandler.SOCIAL_PROVIDER_MAP_CONTEXT_KEY);
            Assertions.assertThat(providersById).containsOnlyKeys(identityProviderWithUrl.getId());
        });

        testRequest(HttpMethod.GET, RootProvider.PATH_LOGIN, 200, "OK", "completed");
    }

    @Test
    public void should_provide_social_and_internal_idp_in_context() throws Exception {
        var applicationIdentityProviderInternal = new ApplicationIdentityProvider();
        applicationIdentityProviderInternal.setIdentity("idpInternal");
        var internalIdentityProvider = new IdentityProvider();
        internalIdentityProvider.setId(applicationIdentityProviderInternal.getIdentity());
        internalIdentityProvider.setExternal(false);
        internalIdentityProvider.setConfiguration(UUID.randomUUID().toString());

        var applicationIdentityProviderSocial = new ApplicationIdentityProvider();
        applicationIdentityProviderSocial.setIdentity("idpSocial");
        var socialIdentityProvider = new IdentityProvider();
        socialIdentityProvider.setId(applicationIdentityProviderSocial.getIdentity());
        socialIdentityProvider.setExternal(true);
        socialIdentityProvider.setConfiguration(UUID.randomUUID().toString());

        when(client.getIdentityProviders()).thenReturn(new TreeSet<>(Set.of(applicationIdentityProviderSocial, applicationIdentityProviderInternal)));
        when(identityProviderManager.getIdentityProvider(socialIdentityProvider.getId())).thenReturn(socialIdentityProvider);
        when(identityProviderManager.getIdentityProvider(internalIdentityProvider.getId())).thenReturn(internalIdentityProvider);
        final var authProvider = Mockito.mock(SocialAuthenticationProvider.class);
        final var request = new Request();
        request.setUri(UUID.randomUUID().toString());
        request.setMethod(io.gravitee.common.http.HttpMethod.GET);
        when(authProvider.asyncSignInUrl(any(), any(), any())).thenReturn(Maybe.just(request));
        when(identityProviderManager.get(eq(socialIdentityProvider.getId()))).thenReturn(Maybe.just(authProvider));

        router.route(HttpMethod.GET, RootProvider.PATH_LOGIN);

        assertAfterRequest(rc -> {
            Assertions.assertThat(rc.data()).containsKeys(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY, ConstantKeys.INTERNAL_PROVIDER_CONTEXT_KEY);
        });

        testRequest(HttpMethod.GET, RootProvider.PATH_LOGIN, 200, "OK", "completed");
    }
}
