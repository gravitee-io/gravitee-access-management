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
package io.gravitee.am.gateway.handler.jwk.impl;

import io.gravitee.am.gateway.handler.jwk.JwkService;
import io.gravitee.am.gateway.handler.oidc.converter.JWKSetDeserializer;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.utils.UriBuilder;
import io.reactivex.Maybe;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URISyntaxException;
import java.util.Optional;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JwkServiceImpl implements JwkService {

    @Autowired
    public WebClient client;

    @Override
    public Maybe<JWKSet> getKeys(String jwksUri) {
        try{
            return client.getAbs(UriBuilder.fromHttpUrl(jwksUri).build().toString())
                    .rxSend()
                    .map(HttpResponse::bodyAsString)
                    .map(new JWKSetDeserializer()::convert)
                    .flatMapMaybe(jwkSet -> {
                        if(jwkSet!=null && jwkSet.isPresent()) {
                            return Maybe.just(jwkSet.get());
                        }
                        return Maybe.empty();
                    })
                    .onErrorResumeNext(Maybe.error(new InvalidClientMetadataException("Unable to parse jwks from : " + jwksUri)));
        }
        catch(IllegalArgumentException | URISyntaxException ex) {
            return Maybe.error(new InvalidClientMetadataException(jwksUri+" is not valid."));
        }
        catch(InvalidClientMetadataException ex) {
            return Maybe.error(ex);
        }
    }

    @Override
    public Maybe<JWK> getKey(JWKSet jwkSet, String kid) {

        if(jwkSet==null || jwkSet.getKeys().isEmpty() || kid==null || kid.trim().isEmpty()) {
            return Maybe.empty();
        }

        //Else return matching key
        Optional<JWK> jwk = jwkSet.getKeys().stream().filter(key -> kid.equals(key.getKid())).findFirst();
        if(jwk.isPresent()) {
            return Maybe.just(jwk.get());
        }

        //No matching key found in JWKs...
        return Maybe.empty();
    }
}
