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
package io.gravitee.am.gateway.handler.oauth2.service.granter.extensiongrant;

import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpHeaders;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.junit.Test;
import org.mockito.Mockito;


public class ExtensionGrantGranterTest {

    @Test
    public void when_ex_msg_is_missing_present_default_one(){
        ExtensionGrantProvider extensionGrantProvider = Mockito.mock();
        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setGrantType("jwt-bearer");

        ExtensionGrantGranter granter = new ExtensionGrantGranter(
                extensionGrantProvider,
                extensionGrant,
                Mockito.mock(),
                Mockito.mock(),
                Mockito.mock(),
                Mockito.mock(),
                Mockito.mock(),
                Mockito.mock(),
                Mockito.mock());

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(new VertxHttpHeaders(new HeadersMultiMap()));
        Client client = new Client();

        Mockito.when(extensionGrantProvider.grant(Mockito.any())).thenReturn(Maybe.error(new RuntimeException("")));

        granter.resolveResourceOwner(tokenRequest, client)
                .test()
                .assertError(ex -> ex.getMessage().equals("Unknown error"));

    }

    @Test
    public void when_ex_msg_is_present_return_it(){
        ExtensionGrantProvider extensionGrantProvider = Mockito.mock();
        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setGrantType("jwt-bearer");

        ExtensionGrantGranter granter = new ExtensionGrantGranter(
                extensionGrantProvider,
                extensionGrant,
                Mockito.mock(),
                Mockito.mock(),
                Mockito.mock(),
                Mockito.mock(),
                Mockito.mock(),
                Mockito.mock(),
                Mockito.mock());

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setParameters(new VertxHttpHeaders(new HeadersMultiMap()));
        Client client = new Client();

        Mockito.when(extensionGrantProvider.grant(Mockito.any())).thenReturn(Maybe.error(new RuntimeException("message")));

        granter.resolveResourceOwner(tokenRequest, client)
                .test()
                .assertError(ex -> ex.getMessage().equals("message"));

    }

}