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
package io.gravitee.am.gateway.handler.oauth2.service.request;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.resources.request.TokenRequestFactory;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenRequestFactoryTest {

    private final TokenRequestFactory tokenRequestFactory = new TokenRequestFactory();

    @Test
    public void shouldCreateRequest_additionalParameters() {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        entries.add(new Parameter<>(Parameters.CLIENT_ID, "client-id"));
        entries.add(new Parameter<>(Parameters.SCOPE, "scope"));
        entries.add(new Parameter<>(Parameters.GRANT_TYPE, "grant_type"));
        entries.add(new Parameter<>("custom", "additional-parameter"));

        io.vertx.core.MultiMap multiMap = mock(io.vertx.core.MultiMap.class);
        when(multiMap.entries()).thenReturn(entries);

        MultiMap rxMultiMap = mock(MultiMap.class);
        when(rxMultiMap.getDelegate()).thenReturn(multiMap);

        io.vertx.core.http.HttpServerRequest httpServerRequest = mock(io.vertx.core.http.HttpServerRequest.class);
        when(httpServerRequest.method()).thenReturn(HttpMethod.POST);

        HttpServerResponse httpServerResponse = mock(HttpServerResponse.class);

        HttpServerRequest rxHttpServerRequest = mock(HttpServerRequest.class);
        when(rxHttpServerRequest.params()).thenReturn(rxMultiMap);
        when(rxHttpServerRequest.params().get(Parameters.CLIENT_ID)).thenReturn("client-id");
        when(rxHttpServerRequest.params().get(Parameters.SCOPE)).thenReturn("scope");
        when(rxHttpServerRequest.params().get(Parameters.GRANT_TYPE)).thenReturn("grant_type");
        when(rxHttpServerRequest.params().entries()).thenReturn(entries);
        when(rxHttpServerRequest.getDelegate()).thenReturn(httpServerRequest);
        when(rxHttpServerRequest.getDelegate().response()).thenReturn(httpServerResponse);

        RoutingContext routingContext = mock(RoutingContext.class);
        when(routingContext.request()).thenReturn(rxHttpServerRequest);
        when(routingContext.get(CONTEXT_PATH)).thenReturn("/test");

        TokenRequest tokenRequest = tokenRequestFactory.create(routingContext);

        Assert.assertNotNull(tokenRequest);
        Assert.assertEquals("client-id", tokenRequest.getClientId());
        Assert.assertTrue(tokenRequest.getAdditionalParameters().size() == 1 && tokenRequest.getAdditionalParameters().containsKey("custom"));
    }

    private class Parameter<K, V> implements Map.Entry<K, V> {
        private final K key;
        private V value;

        public Parameter(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            V old = this.value;
            this.value = value;
            return old;
        }
    }
}
