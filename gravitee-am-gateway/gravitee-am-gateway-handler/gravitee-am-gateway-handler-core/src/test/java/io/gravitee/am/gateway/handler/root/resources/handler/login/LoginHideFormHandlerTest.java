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
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.http.HttpMethod;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class LoginHideFormHandlerTest extends RxWebTestBase {

    public static final int CONTEXT_SETUP = -1;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        router.get(RootProvider.PATH_LOGIN)
                .handler(new LoginHideFormHandler(new Domain()))
                .handler(rc -> rc.response().setStatusCode(200).send("completed"))
                .failureHandler(c -> c.response()
                        .setStatusCode(500)
                        .send(c.failure().getClass().getSimpleName() + ": " + c.failure().getMessage()));
    }

    @Test
    public void shouldDoNothingWhenHideFormDisabled() throws Exception {
        router.route(HttpMethod.GET, RootProvider.PATH_LOGIN)
                .order(CONTEXT_SETUP)
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, minimalClient(false));
                    rc.next();
                });
        testRequest(HttpMethod.GET, RootProvider.PATH_LOGIN, 200, "OK", "completed");
    }

    @Test
    public void shouldDoNothingWhenHideFormEnabledButMultipleProviders() throws Exception {
        router.route(HttpMethod.GET, RootProvider.PATH_LOGIN)
                .order(CONTEXT_SETUP)
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, minimalClient(true));
                    rc.put(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY, List.of(minimalProvider("idp-a"), minimalProvider("idp-b")));
                    rc.next();
                });
        testRequest(HttpMethod.GET, RootProvider.PATH_LOGIN, 200, "OK", "completed");
    }

    @Test
    public void shouldRedirectToProviderWhenHideFormEnabledAndOneProvider() throws Exception {
        final String idpId = "idp-a";
        final String idpUrl = "http://localhost/test-idp";
        router.route(HttpMethod.GET, RootProvider.PATH_LOGIN)
                .order(CONTEXT_SETUP)
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, minimalClient(true));
                    rc.put(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY, List.of(minimalProvider(idpId)));
                    rc.put(LoginAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, Map.of(idpId, idpUrl));
                    rc.next();
                });
        testRequest(HttpMethod.GET, RootProvider.PATH_LOGIN, req -> {}, res -> Assertions.assertThat(res.getHeader("Location")).isEqualTo(idpUrl),302, "Found", null);
    }

    @Test
    public void shouldRedirectToRedirectUriWithError() throws Exception {
        final String idpId = "idp-a";
        final String idpUrl = "http://localhost/test-idp";
        router.route(HttpMethod.GET, RootProvider.PATH_LOGIN)
                .order(CONTEXT_SETUP)
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, minimalClient(true));
                    rc.put(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY, List.of(minimalProvider(idpId)));
                    rc.put(LoginAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, Map.of(idpId, idpUrl));
                    rc.next();
                });

        final var errorQueryParams = "error=test-error&error_code=test-error-code&error_description=test-error-from-test";
        testRequest(HttpMethod.GET,
                RootProvider.PATH_LOGIN+"?redirect_uri=some-redirect&" + errorQueryParams,
                req -> {},
                res -> Assertions.assertThat(res.getHeader("Location"))
                        .isEqualTo("some-redirect?" + errorQueryParams),
                302,
                "Found",
                null);
    }

    @Test
    public void shouldFailWithErrorIfNoRedirectUri() throws Exception {
        final String idpId = "idp-a";
        final String idpUrl = "http://localhost/test-idp";
        router.route(HttpMethod.GET, RootProvider.PATH_LOGIN)
                .order(CONTEXT_SETUP)
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, minimalClient(true));
                    rc.put(ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY, List.of(minimalProvider(idpId)));
                    rc.put(LoginAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, Map.of(idpId, idpUrl));
                    rc.next();
                });

        final var errorQueryParams = "error=test-error&error_code=test-error-code&error_description=test-error-from-test";
        testRequest(HttpMethod.GET,
                RootProvider.PATH_LOGIN + "?" + errorQueryParams,
                req -> {},
                res -> res.bodyHandler(buf -> Assertions.assertThat(buf.toString()).startsWith("IllegalStateException")),
                500,
                "Internal Server Error",
                null);
    }

    private IdentityProvider minimalProvider(String id) {
        var idp = new IdentityProvider();
        idp.setId(id);
        return idp;
    }

    private Client minimalClient(boolean hideForm) {
        var client = new Client();
        client.setLoginSettings(new LoginSettings());
        client.getLoginSettings().setInherited(false);
        client.getLoginSettings().setHideForm(hideForm);
        return client;

    }
}
