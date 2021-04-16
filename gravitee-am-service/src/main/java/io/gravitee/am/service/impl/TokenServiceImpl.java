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

import io.gravitee.am.model.Application;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.TotalToken;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class TokenServiceImpl implements TokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenServiceImpl.class);

    @Autowired
    private ApplicationService applicationService;

    @Lazy
    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Lazy
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Override
    public Single<TotalToken> findTotalTokensByDomain(String domain) {
        LOGGER.debug("Find total tokens by domain: {}", domain);
        return applicationService
            .findByDomain(domain)
            .flatMapObservable(pagedApplications -> Observable.fromIterable(pagedApplications))
            .flatMapSingle(this::countByClientId)
            .toList()
            .flatMap(
                totalAccessTokens -> {
                    TotalToken totalToken = new TotalToken();
                    totalToken.setTotalAccessTokens(totalAccessTokens.stream().mapToLong(Long::longValue).sum());
                    return Single.just(totalToken);
                }
            )
            .onErrorResumeNext(
                ex -> {
                    LOGGER.error("An error occurs while trying to find total tokens by domain: {}", domain, ex);
                    return Single.error(
                        new TechnicalManagementException(
                            String.format("An error occurs while trying to find total tokens by domain: %s", domain),
                            ex
                        )
                    );
                }
            );
    }

    @Override
    public Single<TotalToken> findTotalTokensByApplication(Application application) {
        LOGGER.debug("Find total tokens by application : {}", application);
        return countByClientId(application)
            .map(
                totalAccessTokens -> {
                    TotalToken totalToken = new TotalToken();
                    totalToken.setTotalAccessTokens(totalAccessTokens);
                    return totalToken;
                }
            )
            .onErrorResumeNext(
                ex -> {
                    LOGGER.error("An error occurs while trying to find total tokens by application: {}", application, ex);
                    return Single.error(
                        new TechnicalManagementException(
                            String.format("An error occurs while trying to find total tokens by application: %s", application),
                            ex
                        )
                    );
                }
            );
    }

    @Override
    public Single<TotalToken> findTotalTokens() {
        LOGGER.debug("Find total tokens");
        return applicationService
            .findAll()
            .flatMapObservable(pagedApplications -> Observable.fromIterable(pagedApplications))
            .flatMapSingle(this::countByClientId)
            .toList()
            .flatMap(
                totalAccessTokens -> {
                    TotalToken totalToken = new TotalToken();
                    totalToken.setTotalAccessTokens(totalAccessTokens.stream().mapToLong(Long::longValue).sum());
                    return Single.just(totalToken);
                }
            )
            .onErrorResumeNext(
                ex -> {
                    LOGGER.error("An error occurs while trying to find total tokens", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find total tokens", ex));
                }
            );
    }

    @Override
    public Completable deleteByUserId(String userId) {
        LOGGER.debug("Delete tokens by user : {}", userId);
        return accessTokenRepository
            .deleteByUserId(userId)
            .andThen(refreshTokenRepository.deleteByUserId(userId))
            .onErrorResumeNext(
                ex -> {
                    LOGGER.error("An error occurs while trying to delete tokens by user {}", userId, ex);
                    return Completable.error(
                        new TechnicalManagementException(
                            String.format("An error occurs while trying to find total tokens by user: %s", userId),
                            ex
                        )
                    );
                }
            );
    }

    private Single<Long> countByClientId(Application application) {
        if (application.getSettings() == null) {
            return Single.just(0l);
        }
        if (application.getSettings().getOauth() == null) {
            return Single.just(0l);
        }
        return accessTokenRepository.countByClientId(application.getSettings().getOauth().getClientId());
    }
}
