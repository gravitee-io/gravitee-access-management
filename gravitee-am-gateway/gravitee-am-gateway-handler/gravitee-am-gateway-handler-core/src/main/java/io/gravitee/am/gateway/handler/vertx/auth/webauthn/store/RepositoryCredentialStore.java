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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.store;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.CredentialService;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.auth.webauthn.Authenticator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RepositoryCredentialStore {

    @Autowired
    private CredentialService credentialService;

    @Autowired
    @Qualifier("managementJwtBuilder")
    private JWTBuilder jwtBuilder;

    @Autowired
    private Domain domain;

    public Future<List<Authenticator>> fetch(Authenticator query) {
        Promise<List<Authenticator>> promise = Promise.promise();

        Single<List<Credential>> fetchCredentials = query.getUserName() != null ?
                credentialService.findByUsername(ReferenceType.DOMAIN, domain.getId(), query.getUserName()).toList() :
                credentialService.findByCredentialId(ReferenceType.DOMAIN, domain.getId(), query.getCredID()).toList();

        fetchCredentials
                .flatMap(credentials -> {
                    if (credentials.isEmpty() && query.getUserName() != null) {
                        // If, when initiating an authentication ceremony, there is no account matching the provided username,
                        // continue the ceremony by invoking navigator.credentials.get() using a syntactically valid
                        // PublicKeyCredentialRequestOptions object that is populated with plausible imaginary values.
                        // Prevent 14.6.2. Username Enumeration (https://www.w3.org/TR/webauthn-2/#sctn-username-enumeration)
                        return Single.zip(
                                generateCredID(query.getUserName(), Claims.sub),
                                generateCredID(query.getUserName(), StandardClaims.PREFERRED_USERNAME), (part1, part2) -> {
                                    MessageDigest md = MessageDigest.getInstance("SHA-512");
                                    SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
                                    secureRandom.setSeed(part1.getBytes());
                                    int nbDevices = secureRandom.nextInt(3) + 1;
                                    int deviceType = secureRandom.nextInt(2) + 1;
                                    List<Authenticator> authenticators = new ArrayList<>(nbDevices);
                                    for (int i = 0; i < nbDevices; i++) {
                                        byte[] salt = new byte[16];
                                        secureRandom.nextBytes(salt);
                                        md.update(salt);
                                        String initialValue = shiftValue(part2, i);
                                        Authenticator authenticator = new Authenticator();
                                        authenticator.setUserName(query.getUserName());
                                        if (deviceType == 1) {
                                            if (i < 2) {
                                                if (initialValue.length() > 27) {
                                                    initialValue = initialValue.substring(0, 27);
                                                }
                                                authenticator.setCredID(initialValue);
                                            } else {
                                                authenticator.setCredID(createCredID(md, initialValue, part1));
                                            }
                                        } else {
                                            if (i < 2) {
                                                authenticator.setCredID(createCredID(md, initialValue, part1));
                                            } else {
                                                if (initialValue.length() > 27) {
                                                    initialValue = initialValue.substring(0, 27);
                                                }
                                                authenticator.setCredID(initialValue);
                                            }
                                        }
                                        authenticators.add(authenticator);
                                    }
                                    return authenticators;
                                });
                    } else {
                        return Single.just(credentials
                                .stream()
                                .map(this::convert)
                                .collect(Collectors.toList()));
                    }
                })
                .subscribe(
                        authenticators -> promise.complete(authenticators),
                        error -> promise.fail(error)
                );

        return promise.future();
    }

    public Future<Void> store(Authenticator authenticator) {
        Promise<Void> promise = Promise.promise();

        credentialService.findByCredentialId(ReferenceType.DOMAIN, domain.getId(), authenticator.getCredID())
                .toList()
                .flatMapCompletable(credentials -> {
                    if (credentials.isEmpty()) {
                        // no credential found, create it
                        return create(authenticator);
                    } else {
                        // update current credentials
                        return Observable.fromIterable(credentials)
                                .flatMapCompletable(credential -> {
                                    credential.setCounter(authenticator.getCounter());
                                    credential.setUpdatedAt(new Date());
                                    return credentialService.update(credential).ignoreElement();
                                });
                    }
                })
                .subscribe(
                        () ->  promise.complete(),
                        error -> promise.fail(error.getMessage())
                );
        return promise.future();
    }

    private Completable create(Authenticator authenticator) {
        Credential credential = new Credential();
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId(domain.getId());
        credential.setUsername(authenticator.getUserName());
        credential.setCredentialId(authenticator.getCredID());
        credential.setPublicKey(authenticator.getPublicKey());
        credential.setCounter(authenticator.getCounter());
        credential.setAaguid(authenticator.getAaguid());
        credential.setAttestationStatementFormat(authenticator.getFmt());
        credential.setAttestationStatement(authenticator.getAttestationCertificates().toString());
        credential.setCreatedAt(new Date());
        credential.setUpdatedAt(credential.getCreatedAt());
        return credentialService.create(credential).ignoreElement();
    }

    private Authenticator convert(Credential credential) {
        if (credential == null) {
            return null;
        }
        Authenticator authenticator = new Authenticator();
        authenticator.setUserName(credential.getUsername());
        authenticator.setCredID(credential.getCredentialId());
        if (credential.getCounter() != null) {
            authenticator.setCounter(credential.getCounter());
        }
        authenticator.setPublicKey(credential.getPublicKey());

        return authenticator;
    }

    private Single<String> generateCredID(String username, String claim) {
        return Single.create(emitter -> {
            String credID = jwtBuilder.sign(new JWT(Collections.singletonMap(claim, username))).split("\\.")[2];
            emitter.onSuccess(credID);
        });
    }

    private static String createCredID(MessageDigest md, String input, String suffix) {
        String result = Base64.getUrlEncoder().encodeToString(md.digest(input.getBytes(StandardCharsets.UTF_8))).replace("=", "") + suffix;
        if (result.length() > 87) {
            result = result.substring(0, 87);
        }
        return result;
    }

    private static String shiftValue(String input, int delta) {
        String credID = "";
        char c;
        for (int j = 0; j < input.length(); j++) {
            c = input.charAt(j);
            char deltaC = (char) (c + delta);
            if (Character.isLetterOrDigit(deltaC)) {
                credID += deltaC;
            } else {
                credID += c;
            }
        }
        return credID;
    }
}
