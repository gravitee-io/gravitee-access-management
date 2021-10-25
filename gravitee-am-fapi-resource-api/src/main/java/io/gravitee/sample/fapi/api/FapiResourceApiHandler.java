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

import com.nimbusds.jose.util.Base64URL;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.impl.jose.JWT;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.cli.MissingArgumentException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;


public class FapiResourceApiHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            final Optional<X509Certificate> x509Certificate = CertUtils.extractPeerCertificate(routingContext);
            if (x509Certificate.isEmpty()) throw new MissingArgumentException("PeerCertificate is missing");
            String thumbprint256 = getThumbprint(x509Certificate.get(), "SHA-256");

            final String auth = routingContext.request().getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && auth.startsWith("Bearer ")) {

                String jwtString = auth.replaceFirst("Bearer ", "");
                final JsonObject jwt = JWT.parse(jwtString).getJsonObject("payload");

                if (jwt.containsKey("cnf") && thumbprint256.equals(jwt.getJsonObject("cnf").getString("x5t#S256"))) {
                    //response ok
                    final int statusCode = 200;
                    routingContext.response()
                            .putHeader("content-type", "application/json")
                            .putHeader("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()))
                            .putHeader("x-fapi-auth-date", routingContext.request().getHeader("x-fapi-auth-date"))
                            .putHeader("x-fapi-customer-ip-address", routingContext.request().getHeader("x-fapi-customer-ip-address"))
                            .putHeader("x-fapi-interaction-id", Optional.ofNullable(routingContext.request().getHeader("x-fapi-interaction-id")).orElse(UUID.randomUUID().toString()))
                            .setStatusCode(statusCode)
                            .end(jwt.encodePrettily()); // return JWT as JSON object
                }
            }

            // default response unauthorized
            routingContext.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(401).end();
        } catch (Exception e) {
            e.printStackTrace();
            routingContext.fail(500, e);
        }

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
