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

import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.gravitee.am.service.exception.ApplicationAlreadyExistsException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class OAuthClientUniquenessValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthClientUniquenessValidator.class);

    @Autowired
    @Lazy
    private ApplicationRepository applicationRepository;

    @Autowired
    @Lazy
    private ProtectedResourceRepository protectedResourceRepository;

    public Completable checkClientIdUniqueness(String domain, String clientId) {
        return findByDomainAndClientId(domain, clientId)
                .flatMapCompletable(hasFound -> {
                    if (hasFound) {
                        return Completable.error(new ApplicationAlreadyExistsException(clientId, domain));
                    }
                    return Completable.complete();
                });
    }

    private Single<Boolean> findByDomainAndClientId(String domain, String clientId) {
        LOGGER.debug("Find application/resource by domain: {} and client_id {}", domain, clientId);
        return applicationRepository.findByDomainAndClientId(domain, clientId).map(found -> true)
                .switchIfEmpty(protectedResourceRepository.findByDomainAndClient(domain, clientId).map(found -> true))
                .switchIfEmpty(Single.just(false))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an application/resource using its domain: {} and client_id : {}", domain, clientId, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an application/resource using its domain: %s, and client_id %s", domain, clientId), ex));
                });
    }
}
