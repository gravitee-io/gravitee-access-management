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
package io.gravitee.am.service.spring;

import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.ResourceRetriever;
import io.gravitee.am.certificate.api.Keys;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.jwt.DefaultJWTBuilder;
import io.gravitee.am.jwt.DefaultJWTParser;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.service.http.WebClientBuilder;
import io.gravitee.am.service.nimbusds.WebClientResourceRetriever;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.InvalidKeyException;
import java.security.Key;

import static io.gravitee.am.common.utils.ConstantKeys.DEFAULT_JWT_OR_CSRF_SECRET;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Slf4j
public class JWTConfiguration {
    public enum RetrieverType {
        JOSE,
        WEBCLIENT
    }

    @Bean("managementSecretKey")
    protected Key key(io.gravitee.node.api.configuration.Configuration configuration) throws InvalidKeyException {
        return Keys.hmacShaKeyFor(signingKeySecret(configuration).getBytes());
    }

    @Bean("managementJwtBuilder")
    protected JWTBuilder jwtBuilder(@Qualifier("managementSecretKey") Key key, io.gravitee.node.api.configuration.Configuration configuration) throws InvalidKeyException {
        SignatureAlgorithm signatureAlgorithm = Keys.hmacShaSignatureAlgorithmFor(signingKeySecret(configuration).getBytes());
        return new DefaultJWTBuilder(key, signatureAlgorithm.getValue(), kid(configuration), issuer(configuration));
    }

    @Bean("managementJwtParser")
    protected JWTParser jwtParser(@Qualifier("managementSecretKey") Key key) throws InvalidKeyException {
        return new DefaultJWTParser(key);
    }

    @Bean("defaultResourceRetriever")
    protected ResourceRetriever defaultResourceRetriever(Vertx vertx,
                                                         @Value("${jwt.jwks.retriever.type:JOSE}") RetrieverType type,
                                                         WebClientBuilder webClientBuilder,
                                                         @Value("${httpClient.timeout:10000}") int connectionTimeout,
                                                         @Value("${httpClient.readTimeout:5000}") int readTimeout) {
        return switch (type) {
            case JOSE -> createJoseResourceRetriever(connectionTimeout, readTimeout);
            case WEBCLIENT -> createWebClientResourceRetriever(vertx, webClientBuilder);
        };
    }

    private ResourceRetriever createWebClientResourceRetriever(Vertx vertx, WebClientBuilder webClientBuilder) {
        log.info("Creating WebClient for all JWKs retrievers");
        WebClient webClient = webClientBuilder.createWebClient(vertx);
        return new WebClientResourceRetriever(webClient);
    }

    private ResourceRetriever createJoseResourceRetriever(@Value("${httpClient.timeout:10000}") int connectionTimeout,
                                                          @Value("${httpClient.readTimeout:5000}") int readTimeout) {
        log.warn("JOSE DefaultResourceRetriever is in use for JWKs retrieval. " +
                "This option is DEPRECATED and will be REMOVED in version 4.14. " +
                "Configure 'jwt.jwks.retriever.type=WEBCLIENT' to switch to WebClient-based retriever.");
        if (connectionTimeout == 0 || readTimeout == 0) {
            return new DefaultResourceRetriever();
        } else {
            return new DefaultResourceRetriever(connectionTimeout, readTimeout);
        }
    }

    private String signingKeySecret(io.gravitee.node.api.configuration.Configuration configuration) {
        return configuration.getProperty("jwt.secret", DEFAULT_JWT_OR_CSRF_SECRET);
    }

    private String issuer(io.gravitee.node.api.configuration.Configuration configuration) {
        return configuration.getProperty("jwt.issuer", "https://gravitee.am");
    }

    private String kid(io.gravitee.node.api.configuration.Configuration configuration) {
        return configuration.getProperty("jwt.kid", "default-gravitee-AM-key");
    }
}
