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
package io.gravitee.am.gateway.handler.oauth2.code.impl;

import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.gravitee.common.utils.UUID;
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

    @Override
    public Single<AuthorizationCode> create(AuthorizationRequest authorizationRequest, User user) {
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode(UUID.random().toString());
        authorizationCode.setClientId(authorizationRequest.getClientId());
        authorizationCode.setSubject(user.getId());
        authorizationCode.setScopes(authorizationRequest.getScopes());
        authorizationCode.setRequestParameters(authorizationRequest.getRequestParameters());
        authorizationCode.setExpireAt(new Date(System.currentTimeMillis() + authorizationCodeValidity));
        authorizationCode.setCreatedAt(new Date());

        return authorizationCodeRepository.create(authorizationCode);
    }

    @Override
    public Maybe<AuthorizationCode> remove(String code, Client client) {
        return authorizationCodeRepository.findByCode(code)
                .switchIfEmpty(Maybe.error(new InvalidRequestException("The authorization code " + code + " is invalid.")))
                .flatMap(authorizationCode -> {
                    if (!authorizationCode.getClientId().equals(client.getClientId())) {
                        return Maybe.error(new InvalidRequestException("The authorization code " + code + " does not belong to the client " + client.getClientId() + "."));
                    }
                    return Maybe.just(authorizationCode);
                })
                .flatMap(authorizationCode -> authorizationCodeRepository.delete(authorizationCode.getId()));
    }
}
