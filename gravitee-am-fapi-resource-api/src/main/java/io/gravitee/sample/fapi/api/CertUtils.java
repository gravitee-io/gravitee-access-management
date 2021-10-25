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
package io.gravitee.sample.fapi.api;

import io.vertx.ext.web.RoutingContext;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertUtils {
    public static String certHeader;

    public static Optional<X509Certificate> extractPeerCertificate(RoutingContext routingContext) throws SSLPeerUnverifiedException {
        Optional<X509Certificate> certificate = Optional.empty();

        String certHeaderValue = (certHeader == null || certHeader.isEmpty()) ? null : routingContext.request().getHeader(certHeader);

        if (certHeaderValue != null) {
            try {
                certHeaderValue = URLDecoder.decode(certHeaderValue.replaceAll("\t", "\n"), Charset.defaultCharset());
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                certificate = Optional.ofNullable((X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certHeaderValue.getBytes())));
            } catch (CertificateException e) {
                System.out.println("CERT READ from Header:" + e.getMessage());
                e.printStackTrace();
                // maybe not an error, try the SSLSession
            }
        }

        SSLSession sslSession = routingContext.request().sslSession();
        if (sslSession != null && certificate.isEmpty()) {
            Certificate[] peerCertificates = sslSession.getPeerCertificates();
            certificate = Optional.ofNullable((X509Certificate) peerCertificates[0]);
        }

        return certificate;
    }
}
