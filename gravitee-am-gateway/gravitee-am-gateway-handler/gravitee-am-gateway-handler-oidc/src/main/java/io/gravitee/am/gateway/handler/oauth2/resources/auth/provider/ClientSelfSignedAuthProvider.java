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
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import javax.net.ssl.SSLSession;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

/**
 * Client Authentication method : self_signed_tls_client_auth
 *
 * <p>
 *  This method of mutual-TLS OAuth client authentication is intended to
 *  support client authentication using self-signed certificates.
 * </p>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientSelfSignedAuthProvider implements ClientAuthProvider {

    private final JWKService jwkService;

    public ClientSelfSignedAuthProvider(JWKService jwkService) {
        this.jwkService = jwkService;
    }

    @Override
    public boolean canHandle(Client client, RoutingContext context) {
        // client_id is a required parameter for tls_client_auth so we are sure to have a client here
        return client != null
                && context.request().sslSession() != null
                && ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH.equals(client.getTokenEndpointAuthMethod());
    }

    @Override
    public void handle(Client client, RoutingContext context, Handler<AsyncResult<Client>> handler) {
        // We ensure that the authentication is done over TLS thanks to the canHandle method which checks for an SSL
        // session
        SSLSession sslSession = context.request().sslSession();

        try {
            Certificate[] peerCertificates = sslSession.getPeerCertificates();
            X509Certificate peerCertificate = (X509Certificate) peerCertificates[0];
            String thumbprint = getThumbprint(peerCertificate, "SHA-1");
            String thumbprint256 = getThumbprint(peerCertificate, "SHA-256");
            jwkService.getKeys(client)
                    .subscribe(
                            jwkSet -> {
                                boolean match = jwkSet.getKeys()
                                        .stream()
                                        .anyMatch(jwk -> thumbprint256.equals(jwk.getX5tS256()) || thumbprint.equals(jwk.getX5t()));
                                if (match) {
                                    handler.handle(Future.succeededFuture(client));
                                } else {
                                    handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: invalid self-signed certificate")));
                                }
                            },
                            throwable -> handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: invalid self-signed certificate"))),
                            () -> handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: missing or unsupported JWK Set"))));
        } catch (Exception ex) {
            handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: missing or unsupported self-signed certificate")));
        }
    }

    private static String getThumbprint(X509Certificate cert, String algorithm)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] der = cert.getEncoded();
        md.update(der);
        byte[] digest = md.digest();
        return Base64URL.encode(digest).toString();
    }
}
