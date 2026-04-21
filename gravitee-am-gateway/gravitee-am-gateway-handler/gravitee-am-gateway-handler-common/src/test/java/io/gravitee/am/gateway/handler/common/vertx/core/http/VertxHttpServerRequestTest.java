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
package io.gravitee.am.gateway.handler.common.vertx.core.http;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VertxHttpServerRequestTest {

    @Test
    public void shouldReturnNullContextPathWhenPathIsRoot() {
        assertNull(new VertxHttpServerRequest(mockHttpServerRequest("/")).contextPath());
        assertNull(new VertxHttpServerRequest(mockHttpServerRequest("//")).contextPath());
        assertNull(new VertxHttpServerRequest(mockHttpServerRequest("///")).contextPath());
    }

    @Test
    public void shouldReturnContextPathWhenPathIsEmpty() {
        assertEquals("", new VertxHttpServerRequest(mockHttpServerRequest("")).contextPath());
    }

    @Test
    public void shouldReturnNullContextPathWhenPathIsNull() {
        VertxHttpServerRequest request = new VertxHttpServerRequest(mockHttpServerRequest(null));

        assertNull(request.contextPath());
    }

    private HttpServerRequest mockHttpServerRequest(String path) {
        HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);
        when(httpServerRequest.path()).thenReturn(path);
        when(httpServerRequest.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        when(httpServerRequest.method()).thenReturn(HttpMethod.GET);
        return httpServerRequest;
    }
}
