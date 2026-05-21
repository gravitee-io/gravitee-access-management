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
package io.gravitee.am.gateway.handler.root.resources.handler.common;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static org.mockito.ArgumentMatchers.any;

public class RedirectUriValidationHandlerTest {

    @Test
    public void when_return_url_is_not_present_validate_redirect_uri(){
        Domain domain = new Domain();
        final var handler = new RedirectUriValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam(ConstantKeys.RETURN_URL_KEY)).thenReturn(null);
        Mockito.when(request.getParam(Parameters.REDIRECT_URI)).thenReturn("http://127.0.0.1/");

        Client client = new Client();
        client.setRedirectUris(List.of("http://127.0.0.1/"));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }

    @Test
    public void when_return_url_is_not_present_reject_invalid_redirect_uri_(){
        Domain domain = new Domain();
        final var handler = new RedirectUriValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam(ConstantKeys.RETURN_URL_KEY)).thenReturn(null);
        Mockito.when(request.getParam(Parameters.REDIRECT_URI)).thenReturn("http://127.0.0.1/");

        Client client = new Client();
        client.setRedirectUris(List.of("http://registered"));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.never()).next();
        Mockito.verify(ctx).fail(any(Throwable.class));
    }

    @Test
    public void validation_should_fail_without_uri_param_but_client_with_multiple_uri(){
        Domain domain = new Domain();
        final var handler = new RedirectUriValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam(ConstantKeys.RETURN_URL_KEY)).thenReturn(null);
        Mockito.when(request.getParam(Parameters.REDIRECT_URI)).thenReturn(null);

        Client client = new Client();
        client.setRedirectUris(List.of("http://registered", "http://registered2"));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.never()).next();
        Mockito.verify(ctx).fail(any(Throwable.class));
    }

    @Test
    public void when_return_url_is_present_validate_redirect_uri_if_present(){
        Domain domain = new Domain();
        final var handler = new RedirectUriValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam(ConstantKeys.RETURN_URL_KEY)).thenReturn("http://127.0.0.2/");
        Mockito.when(request.getParam(Parameters.REDIRECT_URI)).thenReturn("http://127.0.0.1/");

        Client client = new Client();
        client.setRedirectUris(List.of("http://127.0.0.1"));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }

    @Test
    public void when_return_url_is_present_let_it_pass(){
        Domain domain = new Domain();
        final var handler = new RedirectUriValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam(ConstantKeys.RETURN_URL_KEY)).thenReturn("http://127.0.0.1/");
        Mockito.when(request.getParam(Parameters.REDIRECT_URI)).thenReturn(null);

        Client client = new Client();
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }

    @Test
    public void when_el_processing_and_strict_matching_is_enabled_validate_only_not_el_params(){
        Domain domain = new Domain();
        ClientRegistrationSettings clientRegistrationSettings = ClientRegistrationSettings.defaultSettings();
        clientRegistrationSettings.setAllowRedirectUriParamsExpressionLanguage(true);
        OIDCSettings oidcSettings = OIDCSettings.defaultSettings();
        oidcSettings.setRedirectUriStrictMatching(true);
        oidcSettings.setClientRegistrationSettings(clientRegistrationSettings);
        domain.setOidc(oidcSettings);
        final var handler = new RedirectUriValidationHandler(domain);
        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam(ConstantKeys.RETURN_URL_KEY)).thenReturn(null);
        Mockito.when(request.getParam(Parameters.REDIRECT_URI)).thenReturn("http://127.0.0.1/?test=1");

        Client client = new Client();
        client.setRedirectUris(List.of("http://127.0.0.1/?param1={#context.attributes['test']}&test=1"));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }

    @Test
    public void cimd_client_rejects_non_exact_redirect_uri_even_when_domain_allows_non_strict_matching() {
        Domain domain = new Domain();
        OIDCSettings oidcSettings = OIDCSettings.defaultSettings();
        oidcSettings.setRedirectUriStrictMatching(false);
        domain.setOidc(oidcSettings);
        final var handler = new RedirectUriValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam(ConstantKeys.RETURN_URL_KEY)).thenReturn(null);
        // Sub-path of the registered URI — would match under non-strict (prefix) matching
        Mockito.when(request.getParam(Parameters.REDIRECT_URI))
                .thenReturn("https://redirect.example.com/callback/sub");

        Client client = new Client();
        client.setClientId("http://example.com/my-app"); // URL-shaped → CIMD client
        client.setRedirectUris(List.of("https://redirect.example.com/callback"));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.never()).next();
        Mockito.verify(ctx).fail(Mockito.any(Throwable.class));
    }

    @Test
    public void loopback_redirect_with_unregistered_port_accepted_when_registered_port_is_unspecified() {
        final var handler = handlerWithLoopbackAllowed();
        RoutingContext ctx = loopbackCtx("http://127.0.0.1:54321/cb", "http://127.0.0.1/cb");

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }

    @Test
    public void loopback_redirect_with_matching_explicit_port_accepted() {
        final var handler = handlerWithLoopbackAllowed();
        RoutingContext ctx = loopbackCtx("http://127.0.0.1:8080/cb", "http://127.0.0.1:8080/cb");

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }

    @Test
    public void loopback_redirect_with_mismatched_explicit_port_rejected() {
        final var handler = handlerWithLoopbackAllowed();
        RoutingContext ctx = loopbackCtx("http://127.0.0.1:9999/cb", "http://127.0.0.1:8080/cb");

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.never()).next();
        Mockito.verify(ctx).fail(any(Throwable.class));
    }

    @Test
    public void loopback_redirect_omitted_default_port_treated_as_default() {
        final var handler = handlerWithLoopbackAllowed();
        RoutingContext ctx = loopbackCtx("http://127.0.0.1/cb", "http://127.0.0.1:80/cb");

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }

    private static RedirectUriValidationHandler handlerWithLoopbackAllowed() {
        Domain domain = new Domain();
        OIDCSettings oidcSettings = OIDCSettings.defaultSettings();
        ClientRegistrationSettings crs = ClientRegistrationSettings.defaultSettings();
        crs.setAllowLocalhostRedirectUri(true);
        oidcSettings.setClientRegistrationSettings(crs);
        domain.setOidc(oidcSettings);
        return new RedirectUriValidationHandler(domain);
    }

    private static RoutingContext loopbackCtx(String requested, String registered) {
        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam(ConstantKeys.RETURN_URL_KEY)).thenReturn(null);
        Mockito.when(request.getParam(Parameters.REDIRECT_URI)).thenReturn(requested);

        Client client = new Client();
        client.setClientId("http://example.com/my-app");
        client.setRedirectUris(List.of(registered));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);
        return ctx;
    }

    @Test
    public void cimd_client_accepts_exact_redirect_uri_match() {
        Domain domain = new Domain();
        OIDCSettings oidcSettings = OIDCSettings.defaultSettings();
        oidcSettings.setRedirectUriStrictMatching(false);
        domain.setOidc(oidcSettings);
        final var handler = new RedirectUriValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam(ConstantKeys.RETURN_URL_KEY)).thenReturn(null);
        Mockito.when(request.getParam(Parameters.REDIRECT_URI))
                .thenReturn("https://redirect.example.com/callback");

        Client client = new Client();
        client.setClientId("http://example.com/my-app"); // URL-shaped → CIMD client
        client.setRedirectUris(List.of("https://redirect.example.com/callback"));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }
}
