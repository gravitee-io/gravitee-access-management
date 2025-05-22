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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static org.mockito.ArgumentMatchers.any;

public class EvaluableRedirectUriValidationHandlerTest {

    @Test
    public void when_return_url_is_not_present_validate_redirect_uri(){
        Domain domain = new Domain();
        final var handler = new RedirectUriValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam(ConstantKeys.RETURN_URL_KEY)).thenReturn(null);
        Mockito.when(request.getParam(io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI)).thenReturn("http://127.0.0.1/");

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
        Mockito.when(request.getParam(io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI)).thenReturn("http://127.0.0.1/");

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
        Mockito.when(request.getParam(io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI)).thenReturn(null);

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
        Mockito.when(request.getParam(io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI)).thenReturn("http://127.0.0.1/");

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
        Mockito.when(request.getParam(io.gravitee.am.common.oauth2.Parameters.REDIRECT_URI)).thenReturn(null);

        Client client = new Client();
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }
}
