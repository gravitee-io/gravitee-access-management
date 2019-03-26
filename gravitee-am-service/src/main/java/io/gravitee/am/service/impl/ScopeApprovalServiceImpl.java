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

import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ScopeApprovalServiceImpl implements ScopeApprovalService {

    public static final Logger LOGGER = LoggerFactory.getLogger(ScopeApprovalServiceImpl.class);

    @Autowired
    private ScopeApprovalRepository scopeApprovalRepository;

    @Override
    public Maybe<ScopeApproval> findById(String id) {
        LOGGER.debug("Find scope approval by id: {}", id);
        return scopeApprovalRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a scope approval by id: {}", id);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a scope approval by id %s", id), ex));
                });
    }

    @Override
    public Single<Set<ScopeApproval>> findByDomainAndUser(String domain, String user) {
        LOGGER.debug("Find scope approvals by domain: {} and user: {}", domain, user);
        return scopeApprovalRepository.findByDomainAndUser(domain, user)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a scope approval for domain: {} and user: {}", domain, user);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a scope approval for domain: %s and user: %s", domain, user), ex));
                });
    }

    @Override
    public Single<Set<ScopeApproval>> findByDomainAndUserAndClient(String domain, String user, String client) {
        LOGGER.debug("Find scope approvals by domain: {} and user: {} and client: {}", domain, user);
        return scopeApprovalRepository.findByDomainAndUserAndClient(domain, user, client)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a scope approval for domain: {}, user: {} and client: {}", domain, user, client);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a scope approval for domain: %s, user: %s and client: %s", domain, user, client), ex));
                });
    }

    @Override
    public Completable revoke(String consentId) {
        LOGGER.debug("Revoke approval for consent: {}", consentId);
        return scopeApprovalRepository.delete(consentId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to revoke approval for scope: {}", consentId);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to revoke approval for scope: %s", consentId), ex));
                });
    }

    @Override
    public Completable revoke(String domain, String user) {
        LOGGER.debug("Revoke approvals for domain: {} and user: {}", domain, user);
        return scopeApprovalRepository.deleteByDomainAndUser(domain, user)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to revoke scope approvals for domain: {} and user : {}", domain, user);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to revoke scope approvals for domain: %s and user: %s", domain, user), ex));
                });
    }

    @Override
    public Completable revoke(String domain, String user, String clientId) {
        LOGGER.debug("Revoke approvals for domain: {}, user: {} and client: {}", domain, user, clientId);
        return scopeApprovalRepository.deleteByDomainAndUserAndClient(domain, user, clientId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to revoke scope approvals for domain: {}, user: {} and client: {}", domain, user, clientId);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to revoke scope approvals for domain: %s, user: %s and client: %s", domain, user, clientId), ex));
                });
    }
}
