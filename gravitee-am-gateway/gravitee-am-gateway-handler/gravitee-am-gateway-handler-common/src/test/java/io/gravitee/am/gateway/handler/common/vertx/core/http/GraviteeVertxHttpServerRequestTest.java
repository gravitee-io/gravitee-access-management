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

import io.gravitee.common.util.LinkedCaseInsensitiveMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.net.ssl.SSLPeerUnverifiedException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class GraviteeVertxHttpServerRequestTest {

    @Test
    public void shouldCreateNewRequest_nominalCase() throws SSLPeerUnverifiedException {
        Request request = mock(Request.class);
        when(request.version()).thenReturn(io.gravitee.common.http.HttpVersion.HTTP_1_1);
        when(request.method()).thenReturn(io.gravitee.common.http.HttpMethod.GET);
        when(request.scheme()).thenReturn("https");
        when(request.uri()).thenReturn("uri");
        when(request.path()).thenReturn("/path");
        when(request.host()).thenReturn("hostname");
        when(request.headers()).thenReturn(HttpHeaders.create().set("h1", "v1"));
        MultiValueMap<String, String> params = new LinkedCaseInsensitiveMultiValueMap<>();
        params.set("p1", "v1");
        when(request.parameters()).thenReturn(params);

        GraviteeVertxHttpServerRequest graviteeVertxHttpServerRequest
                = new GraviteeVertxHttpServerRequest(request);

        Assert.assertEquals(HttpVersion.HTTP_1_1, graviteeVertxHttpServerRequest.version());
        Assert.assertEquals(HttpMethod.GET, graviteeVertxHttpServerRequest.method());
        Assert.assertEquals(request.scheme(), graviteeVertxHttpServerRequest.scheme());
        Assert.assertEquals(request.uri(), graviteeVertxHttpServerRequest.uri());
        Assert.assertEquals(request.path(), graviteeVertxHttpServerRequest.path());
        Assert.assertEquals(request.host(), graviteeVertxHttpServerRequest.host());
        Assert.assertEquals("v1", graviteeVertxHttpServerRequest.getHeader("h1"));
        Assert.assertEquals("v1", graviteeVertxHttpServerRequest.getParam("p1"));

        // all other values must be set to default
        Assert.assertEquals(graviteeVertxHttpServerRequest, graviteeVertxHttpServerRequest.exceptionHandler(null));
        Assert.assertEquals(graviteeVertxHttpServerRequest, graviteeVertxHttpServerRequest.handler(null));
        Assert.assertEquals(graviteeVertxHttpServerRequest, graviteeVertxHttpServerRequest.pause());
        Assert.assertEquals(graviteeVertxHttpServerRequest, graviteeVertxHttpServerRequest.resume());
        Assert.assertEquals(graviteeVertxHttpServerRequest, graviteeVertxHttpServerRequest.fetch(0l));
        Assert.assertEquals(graviteeVertxHttpServerRequest, graviteeVertxHttpServerRequest.endHandler(null));
        Assert.assertEquals(0, graviteeVertxHttpServerRequest.peerCertificateChain().length);
        Assert.assertEquals(0l, graviteeVertxHttpServerRequest.bytesRead());
        Assert.assertNull(graviteeVertxHttpServerRequest.query());
        Assert.assertNull(graviteeVertxHttpServerRequest.response());
        Assert.assertNull(graviteeVertxHttpServerRequest.setParamsCharset("any"));
        Assert.assertNull(graviteeVertxHttpServerRequest.getParamsCharset());
        Assert.assertNull(graviteeVertxHttpServerRequest.absoluteURI());
        Assert.assertTrue(graviteeVertxHttpServerRequest.body().isComplete());
        Assert.assertTrue(graviteeVertxHttpServerRequest.end().isComplete());
        Assert.assertTrue(graviteeVertxHttpServerRequest.toNetSocket().isComplete());
        Assert.assertEquals(graviteeVertxHttpServerRequest, graviteeVertxHttpServerRequest.setExpectMultipart(false));
        Assert.assertFalse(graviteeVertxHttpServerRequest.isExpectMultipart());
        Assert.assertEquals(graviteeVertxHttpServerRequest, graviteeVertxHttpServerRequest.uploadHandler(null));
        Assert.assertTrue(graviteeVertxHttpServerRequest.formAttributes().isEmpty());
        Assert.assertNull(graviteeVertxHttpServerRequest.getFormAttribute("any"));
        Assert.assertTrue(graviteeVertxHttpServerRequest.toWebSocket().isComplete());
        Assert.assertFalse(graviteeVertxHttpServerRequest.isEnded());
        Assert.assertEquals(graviteeVertxHttpServerRequest, graviteeVertxHttpServerRequest.customFrameHandler(null));
        Assert.assertNull(graviteeVertxHttpServerRequest.connection());
        Assert.assertEquals(graviteeVertxHttpServerRequest, graviteeVertxHttpServerRequest.streamPriorityHandler(null));
        Assert.assertNull(graviteeVertxHttpServerRequest.decoderResult());
        Assert.assertNull(graviteeVertxHttpServerRequest.getCookie("any"));
        Assert.assertNull(graviteeVertxHttpServerRequest.getCookie("any", "any", "any"));
        Assert.assertNull(graviteeVertxHttpServerRequest.cookies("any"));
        Assert.assertNull(graviteeVertxHttpServerRequest.cookies());
    }
}
