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

import com.nimbusds.jose.util.Base64URL;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateUtils.class);

    public static boolean hasPeerCertificate(RoutingContext routingContext, String certHeader) {
        String certHeaderValue = StringUtils.isEmpty(certHeader) ? null : routingContext.request().getHeader(certHeader);
        return routingContext.request().sslSession() != null || certHeaderValue != null;
    }

    /**
     * This method gets the PeerCertificate from the sslSession.
     * If no sslSession is available, the certificate from the HTTP Header using the header name provided as parameter.
     *
     * @param routingContext
     * @param certHeader
     * @return
     * @throws SSLPeerUnverifiedException
     * @throws CertificateException
     */
    public static Optional<X509Certificate> extractPeerCertificate(RoutingContext routingContext, String certHeader) throws SSLPeerUnverifiedException {
        Optional<X509Certificate> certificate = Optional.empty();

        String certHeaderValue = StringUtils.isEmpty(certHeader) ? null : routingContext.request().getHeader(certHeader);

        if (certHeaderValue != null) {
            try {
                certHeaderValue = URLDecoder.decode(certHeaderValue.replaceAll("\t", "\n"), Charset.defaultCharset());
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

    public static String getThumbprint(X509Certificate cert, String algorithm)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] der = cert.getEncoded();
        md.update(der);
        byte[] digest = md.digest();
        return Base64URL.encode(digest).toString();
    }
}
