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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.ReturnUrlMismatchException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.junit.jupiter.api.Assertions.*;

public class ReturnUrlValidationHandlerTest {

    @Test
    public void when_return_url_is_not_present_let_it_pass(){
        Domain domain = new Domain();
        ReturnUrlValidationHandler handler = new ReturnUrlValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam("return_url")).thenReturn(null);

        Client client = new Client();
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }

    @Test
    public void when_return_url_is_present_and_registered_uris_is_not_empty_validate_it(){
        Domain domain = new Domain();
        ReturnUrlValidationHandler handler = new ReturnUrlValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam("return_url")).thenReturn("https://onet.pl");

        Client client = new Client();
        client.setRedirectUris(List.of("https://onet.pl"));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }

    @Test
    public void when_return_url_is_present_and_it_matches_with_request_url_validate_it(){
        Domain domain = new Domain();
        ReturnUrlValidationHandler handler = new ReturnUrlValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(ctx.get(CONTEXT_PATH)).thenReturn("/goto");
        Mockito.when(request.scheme()).thenReturn("http");
        Mockito.when(request.host()).thenReturn("somedomain.com");
        Mockito.when(request.getParam("return_url")).thenReturn("http://somedomain.com/goto");

        Client client = new Client();
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }

    @Test
    public void when_return_url_is_present_and_registered_uris_is_not_empty_invalidate_it(){
        Domain domain = new Domain();
        ReturnUrlValidationHandler handler = new ReturnUrlValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam("return_url")).thenReturn("https://onet.pl");

        Client client = new Client();
        client.setRedirectUris(List.of("https://different.pl"));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).fail(Mockito.argThat(th -> th instanceof ReturnUrlMismatchException));
    }

    @Test
    public void when_return_url_is_present_and_registered_uris_is_not_empty_invalidate_it__different_schema(){
        Domain domain = new Domain();
        ReturnUrlValidationHandler handler = new ReturnUrlValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam("return_url")).thenReturn("https://onet.pl");

        Client client = new Client();
        client.setRedirectUris(List.of("http://onet.pl"));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).fail(Mockito.argThat(th -> th instanceof ReturnUrlMismatchException));
    }

    @Test
    public void when_return_url_is_present_but_contains_user_info_should_fail(){
        Domain domain = new Domain();
        ReturnUrlValidationHandler handler = new ReturnUrlValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(ctx.get(CONTEXT_PATH)).thenReturn("/goto");
        Mockito.when(request.scheme()).thenReturn("http");
        Mockito.when(request.host()).thenReturn("somedomain.com");
        Mockito.when(request.getParam("return_url")).thenReturn("http://user@somedomain.com/goto");

        Client client = new Client();
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).fail(Mockito.argThat(th -> th instanceof ReturnUrlMismatchException));
    }

    @Test
    public void when_return_url_is_registered_mobile_link_should_pass(){
        Domain domain = new Domain();
        ReturnUrlValidationHandler handler = new ReturnUrlValidationHandler(domain);

        RoutingContext ctx = Mockito.mock();
        HttpServerRequest request = Mockito.mock();
        Mockito.when(ctx.request()).thenReturn(request);
        Mockito.when(request.getParam("return_url")).thenReturn("somedomain.pl:/");

        Client client = new Client();
        client.setRedirectUris(List.of("somedomain.pl:/"));
        Mockito.when(ctx.get(CLIENT_CONTEXT_KEY)).thenReturn(client);

        handler.handle(ctx);

        Mockito.verify(ctx, Mockito.times(1)).next();
    }

}