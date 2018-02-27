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
package io.gravitee.am.service.impl;

import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.TotalToken;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class TokenServiceImpl implements TokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenServiceImpl.class);

    @Autowired
    private ClientService clientService;

    @Autowired
    private TokenRepository tokenRepository;

    @Override
    public Single<TotalToken> findTotalTokensByDomain(String domain) {
        LOGGER.debug("Find total tokens by domain: {}", domain);
        return clientService.findByDomain(domain)
                .flatMapObservable(clients -> Observable.fromIterable(clients))
                .flatMapSingle(client -> tokenRepository.findTokensByClientId(client.getClientId()).flatMap(oAuth2AccessTokens -> Single.just(oAuth2AccessTokens.size())))
                .toList()
                .flatMap(totalAccessTokens -> {
                    TotalToken totalToken = new TotalToken();
                    totalToken.setTotalAccessTokens(totalAccessTokens.stream().mapToLong(Integer::intValue).sum());
                    return Single.just(totalToken);
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find total tokens by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find total tokens by domain: %s", domain), ex));
                });
    }

    @Override
    public Single<TotalToken> findTotalTokens() {
        LOGGER.debug("Find total tokens");
        return clientService.findAll()
                .flatMapObservable(clients -> Observable.fromIterable(clients))
                .flatMapSingle(client -> tokenRepository.findTokensByClientId(client.getClientId()).flatMap(oAuth2AccessTokens -> Single.just(oAuth2AccessTokens.size())))
                .toList()
                .flatMap(totalAccessTokens -> {
                    TotalToken totalToken = new TotalToken();
                    totalToken.setTotalAccessTokens(totalAccessTokens.stream().mapToLong(Integer::intValue).sum());
                    return Single.just(totalToken);
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find total tokens", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find total tokens", ex));
                });
    }
}
