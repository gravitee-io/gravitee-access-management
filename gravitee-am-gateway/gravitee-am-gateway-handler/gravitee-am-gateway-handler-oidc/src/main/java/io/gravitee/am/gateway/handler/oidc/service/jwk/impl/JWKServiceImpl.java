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
package io.gravitee.am.gateway.handler.oidc.service.jwk.impl;

import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.converter.JWKSetDeserializer;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JWKServiceImpl implements JWKService {
    private static final Set<String> JWK_USAGE = Set.of("sig", "enc");

    @Autowired
    private CertificateManager certificateManager;

    @Autowired
    @Qualifier("oidcWebClient")
    public WebClient client;

    @Override
    public Single<JWKSet> getKeys() {
        return Flowable.fromIterable(certificateManager.providers())
                .subscribeOn(Schedulers.io())
                .flatMap(certificateProvider -> certificateProvider.getProvider().keys())
                .observeOn(Schedulers.computation())
                .filter(jwk -> jwk.getUse() == null || JWK_USAGE.contains(jwk.getUse()))
                .toList()
                .map(keys -> {
                    JWKSet jwkSet = new JWKSet();
                    jwkSet.setKeys(keys);
                    return jwkSet;
                });
    }

    @Override
    public Maybe<JWKSet> getKeys(Client client) {
        if (client.getJwks() != null) {
            return Maybe.just(client.getJwks());
        } else if (client.getJwksUri() != null) {
            return getKeys(client.getJwksUri());
        }
        return Maybe.empty();
    }

    @Override
    public Maybe<JWKSet> getDomainPrivateKeys() {
        return Flowable.fromIterable(certificateManager.providers())
                .subscribeOn(Schedulers.io())
                .flatMap(provider -> provider.getProvider().privateKey())
                .subscribeOn(Schedulers.computation())
                .toList()
                .map(keys -> {
                    JWKSet jwkSet = new JWKSet();
                    jwkSet.setKeys(keys);
                    return jwkSet;
                }).toMaybe();
    }

    @Override
    public Maybe<JWKSet> getKeys(String jwksUri) {
        try {
            return client.getAbs(UriBuilder.fromHttpUrl(jwksUri).build().toString())
                    .rxSend()
                    .map(HttpResponse::bodyAsString)
                    .map(new JWKSetDeserializer()::convert)
                    .flatMapMaybe(jwkSet -> {
                        if (jwkSet != null && jwkSet.isPresent()) {
                            return Maybe.just(jwkSet.get());
                        }
                        return Maybe.empty();
                    })
                    .onErrorResumeNext(exception -> Maybe.error(new InvalidClientMetadataException("Unable to parse jwks from : " + jwksUri)));
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return Maybe.error(new InvalidClientMetadataException(jwksUri + " is not valid."));
        } catch (InvalidClientMetadataException ex) {
            return Maybe.error(ex);
        }
    }

    @Override
    public Maybe<JWK> getKey(JWKSet jwkSet, String kid) {

        if (jwkSet == null || jwkSet.getKeys().isEmpty() || kid == null || kid.trim().isEmpty()) {
            return Maybe.empty();
        }

        //Else return matching key
        Optional<JWK> jwk = jwkSet.getKeys().stream().filter(key -> kid.equals(key.getKid())).findFirst();

        //empty if no matching key found in JWKs...
        return jwk.map(Maybe::just).orElseGet(Maybe::empty);
    }

    @Override
    public Maybe<JWK> filter(JWKSet jwkSet, Predicate<JWK> filter) {
        if (jwkSet == null || jwkSet.getKeys() == null || jwkSet.getKeys().isEmpty()) {
            return Maybe.empty();
        }

        Optional<JWK> jwk = jwkSet.getKeys()
                .stream()
                .filter(filter)
                .findFirst();

        return jwk.map(Maybe::just).orElseGet(Maybe::empty);
    }
}
