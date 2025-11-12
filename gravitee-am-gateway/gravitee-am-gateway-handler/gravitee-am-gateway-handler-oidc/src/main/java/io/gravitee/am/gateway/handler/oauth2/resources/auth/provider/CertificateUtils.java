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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.provider;

import io.gravitee.am.certificate.api.X509CertUtils;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CertificateUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateUtils.class);

    public static boolean hasPeerCertificate(RoutingContext routingContext, String certHeader) {
        String certHeaderValue = StringUtils.hasText(certHeader) ? routingContext.request().getHeader(certHeader) : null;
        return routingContext.request().sslSession() != null || certHeaderValue != null;
    }

    /**
     * This method gets the PeerCertificate from the SSL session.
     * If no SSL session is available, it retrieves the certificate from the HTTP header using the header name provided as a parameter.
     *
     * @param routingContext the routing context which provides access to the HTTP request and SSL session.
     * @param certHeader the name of the HTTP header that may contain the certificate.
     * @return an Optional containing the X509Certificate if found, otherwise an empty Optional.
     * @throws SSLPeerUnverifiedException if the peer's identity has not been verified.
     */
    public static Optional<X509Certificate> extractPeerCertificate(RoutingContext routingContext, String certHeader) throws SSLPeerUnverifiedException {
        Optional<X509Certificate> certificate = Optional.empty();

        String certHeaderValue = StringUtils.hasText(certHeader) ? routingContext.request().getHeader(certHeader) : null;

        if (certHeaderValue != null) {
            try {
                certHeaderValue = certHeaderValue
                        .replace("+", "%2B")
                        .replace("/", "%2F")
                        .replace("=", "%3D")
                        .replace("\t", "\n");

                certHeaderValue = URLDecoder.decode(certHeaderValue, Charset.defaultCharset());
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                certificate = Optional.ofNullable((X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certHeaderValue.getBytes())));
            } catch (CertificateException e) {
                LOGGER.debug("Peer Certificate header is present but certificate can't be read, try with the sslSession (cause: {})", e.getMessage());
            }
        }

        SSLSession sslSession = routingContext.request().sslSession();
        if (sslSession != null && certificate.isEmpty()) {
            Certificate[] peerCertificates = sslSession.getPeerCertificates();
            certificate = Optional.ofNullable((X509Certificate) peerCertificates[0]);
        }

        return certificate;
    }

    /**
     * Calculate certificate thumbprint using the specified algorithm.
     * Delegates to X509CertUtils.getThumbprint for consistency across the codebase.
     *
     * @param cert the X.509 certificate
     * @param algorithm the hash algorithm (e.g., "SHA-256")
     * @return Base64URL-encoded thumbprint
     * @throws NoSuchAlgorithmException if the algorithm is not available
     * @throws CertificateEncodingException if the certificate cannot be encoded
     */
    public static String getThumbprint(X509Certificate cert, String algorithm)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        return X509CertUtils.getThumbprint(cert, algorithm);
    }
}
