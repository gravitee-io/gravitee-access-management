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

package io.gravitee.am.gateway.handler.common.utils;


import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateUtilsTest {

    public static final String HEADER = "X-HEADER";

    @Test
    public void testCertificateValidation_FromHeader_NotEncoded() throws Exception {
        var certPayload = "-----BEGIN CERTIFICATE-----\n" +
                "MIICCTCCAa+gAwIBAgIUN7ooxea0kJHv18V9kpQ7Xen2gSowCgYIKoZIzj0EAwIw\n" +
                "WjELMAkGA1UEBhMCRlIxDTALBgNVBAgMBE5vcmQxDjAMBgNVBAcMBUxpbGxlMRcw\n" +
                "FQYDVQQKDA5NeU9yZ2FuaXphdGlvbjETMBEGA1UEAwwKQ29tbW9uTmFtZTAeFw0y\n" +
                "NTA1MjIxNzU4NTRaFw0yNTA2MjExNzU4NTRaMFoxCzAJBgNVBAYTAkZSMQ0wCwYD\n" +
                "VQQIDAROb3JkMQ4wDAYDVQQHDAVMaWxsZTEXMBUGA1UECgwOTXlPcmdhbml6YXRp\n" +
                "b24xEzARBgNVBAMMCkNvbW1vbk5hbWUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNC\n" +
                "AARRtfM1dgex0RW2Zf+vbWX1NCKxxVVmreKn3zMGuDGjFlqWc0VKe2wQal032H3H\n" +
                "qaH2ju/wPHhihhIPE1i7m7alo1MwUTAdBgNVHQ4EFgQUUkMQCUtFHNyGoZ0Qv+gf\n" +
                "mpsH/iEwHwYDVR0jBBgwFoAUUkMQCUtFHNyGoZ0Qv+gfmpsH/iEwDwYDVR0TAQH/\n" +
                "BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiByzweeBYRCDSesw/3++jcesRZddaxE\n" +
                "yhFkQJuzSYRwiQIhAK9WZoFE8dCVi8403a8e5jql6PKwkVjVt4ZX/bWAeq5U\n" +
                "-----END CERTIFICATE-----\n";

        RoutingContext routingContext = Mockito.mock(RoutingContext.class);
        HttpServerRequest httpRequest = Mockito.mock(HttpServerRequest.class);
        Mockito.when(routingContext.request()).thenReturn(httpRequest);
        Mockito.when(httpRequest.getHeader(HEADER)).thenReturn(certPayload);

        var cert = CertificateUtils.extractPeerCertificate(routingContext, HEADER);
        Assertions.assertTrue(cert.isPresent());
    }
}
