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
package io.gravitee.am.gateway.handler.oauth2.service.code.impl;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationCodeServiceImpl implements AuthorizationCodeService {

    @Value("${authorization.code.validity:60000}")
    private int authorizationCodeValidity;

    @Autowired
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Override
    public Single<AuthorizationCode> create(AuthorizationRequest authorizationRequest, User user) {
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setId(RandomString.generate());
        authorizationCode.setTransactionId(authorizationRequest.transactionId());
        authorizationCode.setCode(SecureRandomString.generate());
        authorizationCode.setClientId(authorizationRequest.getClientId());
        authorizationCode.setSubject(user.getId());
        authorizationCode.setScopes(authorizationRequest.getScopes());
        authorizationCode.setRequestParameters(authorizationRequest.parameters());
        authorizationCode.setExpireAt(new Date(System.currentTimeMillis() + authorizationCodeValidity));
        authorizationCode.setCreatedAt(new Date());

        return authorizationCodeRepository.create(authorizationCode);
    }

    @Override
    public Maybe<AuthorizationCode> remove(String code, Client client) {
        return authorizationCodeRepository.findByCode(code)
                .switchIfEmpty(handleInvalidCode(code))
                .flatMap(authorizationCode -> {
                    if (!authorizationCode.getClientId().equals(client.getClientId())) {
                        return Maybe.error(new InvalidGrantException("The authorization code " + code + " does not belong to the client " + client.getClientId() + "."));
                    }
                    return Maybe.just(authorizationCode);
                })
                .flatMap(authorizationCode -> authorizationCodeRepository.delete(authorizationCode.getId()));
    }


    private Maybe<AuthorizationCode> handleInvalidCode(String code) {
        // The client MUST NOT use the authorization code more than once.
        // If an authorization code is used more than once, the authorization server MUST deny the request and SHOULD
        // revoke (when possible) all tokens previously issued based on that authorization code.
        // https://tools.ietf.org/html/rfc6749#section-4.1.2
        return accessTokenRepository.findByAuthorizationCode(code)
                .flatMapCompletable(accessToken -> {
                    Completable deleteAccessTokenAction = accessTokenRepository.delete(accessToken.getToken());
                    if (accessToken.getRefreshToken() != null) {
                        deleteAccessTokenAction.andThen(refreshTokenRepository.delete(accessToken.getRefreshToken()));
                    }
                    return deleteAccessTokenAction;
                })
                .andThen(Maybe.error(new InvalidGrantException("The authorization code " + code + " is invalid.")));
    }
}
