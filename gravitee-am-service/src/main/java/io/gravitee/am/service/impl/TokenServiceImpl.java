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
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class TokenServiceImpl implements TokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenServiceImpl.class);

    @Lazy
    @Autowired
    private AccessTokenRepository accessTokenRepository;

    @Lazy
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditService auditService;

    @Override
    public Completable deleteByUser(User user) {
        LOGGER.debug("Delete tokens by user : {}", user.getId());
        var userId = user.getId();
        return accessTokenRepository.deleteByUserId(userId)
                .andThen(refreshTokenRepository.deleteByUserId(userId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete tokens by user {}", userId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete tokens by user: %s", userId), ex));
                })
                .doOnEvent(error -> auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class)
                                        .reference(new Reference(user.getReferenceType(), user.getReferenceId()))
                                        .tokenActor(user)
                                        .revoked("All tokens are revoked for user: " + userId)
                                        .throwable(error)));
    }

    @Override
    public Completable deleteByApplication(Application application) {
        LOGGER.debug("Delete tokens by application : {}", application);
        var clientId = Optional.ofNullable(application.getSettings())
                .map(ApplicationSettings::getOauth)
                .map(ApplicationOAuthSettings::getClientId);
        return clientId.isEmpty() ? Completable.complete() : accessTokenRepository.deleteByDomainIdAndClientId(application.getDomain(), clientId.get())
                .andThen(refreshTokenRepository.deleteByDomainIdAndClientId(application.getDomain(), clientId.get()))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete tokens by client {}", clientId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete tokens by client: %s", clientId), ex));
                })
                .doOnEvent(error -> auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class)
                        .reference(Reference.domain(application.getDomain()))
                        .tokenActor(application.toClient())
                        .revoked("All tokens are revoked for client: " + clientId)
                        .throwable(error)));
    }
}
