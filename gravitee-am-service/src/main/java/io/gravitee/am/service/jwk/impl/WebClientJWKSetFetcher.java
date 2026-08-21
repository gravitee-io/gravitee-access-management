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
import io.gravitee.am.service.utils.vertx.BoundedBufferWriteStream;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.core.streams.WriteStream;
import io.vertx.rxjava3.ext.web.codec.BodyCodec;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.CustomLog;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RequiredArgsConstructor
@CustomLog
public class WebClientJWKSetFetcher implements JWKSetFetcher {
    private final WebClient client;

    private final static JWKSetDeserializer JWK_SET_DESERIALIZER = new JWKSetDeserializer();

    @Override
    public Maybe<JWKSetFetchResponse> getKeys(String jwksUri) {
        return fetch(jwksUri, 0L);
    }

    @Override
    public Maybe<JWKSetFetchResponse> getKeys(String jwksUri, long maxResponseSizeBytes) {
        return fetch(jwksUri, maxResponseSizeBytes);
    }

    private Maybe<JWKSetFetchResponse> fetch(String jwksUri, long maxResponseSizeBytes) {
        try {
            String url = UriBuilder.fromHttpUrl(jwksUri).build().toString();
            return send(url, maxResponseSizeBytes)
                    .flatMapMaybe(res -> {
                        if (res.statusCode() == 200) {
                            return Maybe.just(res);
                        }
                        String errorMessage = String.format(
                                "HTTP status %s retrieving JWK set from %s. Body: %s",
                                res.statusCode(),
                                url,
                                res.body());
                        return Maybe.error(new IOException(errorMessage));
                    })
                    .flatMap(this::toResponse)
                    .onErrorResumeNext(exception -> Maybe.error(oversized(exception)
                            ? new InvalidClientMetadataException("JWK set from " + jwksUri + " exceeds the maximum allowed response size")
                            : new InvalidClientMetadataException("Unable to parse jwks from : " + jwksUri)));
        } catch (IllegalArgumentException | URISyntaxException ex) {
            log.debug("Unable to parse jwks from : {}", jwksUri, ex);
            return Maybe.error(new InvalidClientMetadataException(jwksUri + " is not valid."));
        } catch (InvalidClientMetadataException ex) {
            log.debug("Unable to parse jwks from : {}", jwksUri, ex);
            return Maybe.error(ex);
        }
    }

    private Single<FetchedResponse> send(String url, long maxResponseSizeBytes) {
        if (maxResponseSizeBytes <= 0) {
            return client.getAbs(url)
                    .rxSend()
                    .map(res -> new FetchedResponse(res.statusCode(), res.bodyAsString(), res.getHeader(HttpHeaders.CONTENT_TYPE)));
        }
        return Single.defer(() -> {
            BoundedBufferWriteStream collector = new BoundedBufferWriteStream(maxResponseSizeBytes);
            return client.getAbs(url)
                    .as(BodyCodec.pipe(WriteStream.newInstance(collector), false))
                    .rxSend()
                    .map(res -> new FetchedResponse(res.statusCode(), bodyOf(collector), res.getHeader(HttpHeaders.CONTENT_TYPE)));
        });
    }

    private static String bodyOf(BoundedBufferWriteStream collector) {
        Buffer body = collector.body();
        return body.length() > 0 ? body.toString(StandardCharsets.UTF_8) : null;
    }

    private static boolean oversized(Throwable exception) {
        return exception instanceof BoundedBufferWriteStream.MaxResponseSizeExceededException;
    }

    private Maybe<JWKSetFetchResponse> toResponse(FetchedResponse response) {
        String raw = response.body();
        Optional<JWKSet> jwkSet = JWK_SET_DESERIALIZER.convert(raw);
        return Maybe.fromOptional(jwkSet)
                .map(res -> new JWKSetFetchResponse(res, new Resource(raw, response.contentType())));
    }

    private record FetchedResponse(int statusCode, String body, String contentType) {}

}
