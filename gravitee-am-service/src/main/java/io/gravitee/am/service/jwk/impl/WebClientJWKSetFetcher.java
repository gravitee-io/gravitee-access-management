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
package io.gravitee.am.service.jwk.impl;

import com.nimbusds.jose.util.Resource;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.jwk.JWKSetFetcher;
import io.gravitee.am.service.utils.jwk.converter.JWKSetDeserializer;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.CustomLog;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;

@RequiredArgsConstructor
@CustomLog
public class WebClientJWKSetFetcher implements JWKSetFetcher {
    private final WebClient client;

    private final static JWKSetDeserializer JWK_SET_DESERIALIZER = new JWKSetDeserializer();

    @Override
    public Maybe<JWKSetFetchResponse> getKeys(String jwksUri) {
        return fetch(jwksUri);
    }

    private Maybe<JWKSetFetchResponse> fetch(String jwksUri) {
        try {
            String url = UriBuilder.fromHttpUrl(jwksUri).build().toString();
            return client.getAbs(url)
                    .rxSend()
                    .flatMapMaybe(res -> {
                        if (res.statusCode() == 200) {
                            return Maybe.just(res);
                        }
                        String errorMessage = String.format(
                                "HTTP status %s retrieving JWK set from %s. Body: %s",
                                res.statusCode(),
                                url,
                                res.bodyAsString());
                        return Maybe.error(new IOException(errorMessage));
                    })
                    .flatMap(this::toResponse)
                    .onErrorResumeNext(exception -> Maybe.error(new InvalidClientMetadataException("Unable to parse jwks from : " + jwksUri)));
        } catch (IllegalArgumentException | URISyntaxException ex) {
            log.debug("Unable to parse jwks from : {}", jwksUri, ex);
            return Maybe.error(new InvalidClientMetadataException(jwksUri + " is not valid."));
        } catch (InvalidClientMetadataException ex) {
            log.debug("Unable to parse jwks from : {}", jwksUri, ex);
            return Maybe.error(ex);
        }
    }

    private Maybe<JWKSetFetchResponse> toResponse(HttpResponse response) {
        String raw = response.bodyAsString();
        Optional<JWKSet> jwkSet = JWK_SET_DESERIALIZER.convert(raw);
        return Maybe.fromOptional(jwkSet)
                .map(res -> new JWKSetFetchResponse(res, new Resource(raw, response.getHeader(HttpHeaders.CONTENT_TYPE))));
    }

}
