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
package io.gravitee.am.management.handlers.management.api.adapter;

import io.gravitee.am.management.handlers.management.api.model.ApplicationEntity;
import io.gravitee.am.management.handlers.management.api.model.AuthorizationDetailApprovalEntity;
import io.gravitee.am.management.handlers.management.api.model.AuthorizationDetailTypeEntity;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.AuthorizationDetailApprovalService;
import io.gravitee.am.service.AuthorizationDetailTypeService;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class AuthorizationDetailApprovalAdapterImpl implements AuthorizationDetailApprovalAdapter {

    private final AuthorizationDetailApprovalService authorizationDetailApprovalService;
    private final AuthorizationDetailTypeService authorizationDetailTypeService;
    private final ConsentApplicationEntityFactory consentApplicationEntityFactory;
    private final DataPlaneRegistry dataPlaneRegistry;

    @Override
    public Single<List<AuthorizationDetailApprovalEntity>> getUserConsents(Domain domain, String rawUserId, String clientId) {
        var userId = UserId.internal(rawUserId);
        return dataPlaneRegistry.getUserRepository(domain)
                .findById(domain.asReference(), userId)
                .switchIfEmpty(Single.error(new UserNotFoundException(userId)))
                .flatMap(user -> authorizationDetailTypeService.findByDomain(domain.getId())
                        .toMap(AuthorizationDetailType::getType)
                        .flatMap(types -> {
                            Map<String, Single<ApplicationEntity>> clients = new HashMap<>();
                            return approvals(domain, user, clientId)
                                    .concatMapSingle(approval -> clients
                                            .computeIfAbsent(approval.getClientId(),
                                                    id -> consentApplicationEntityFactory.resolve(approval.getDomain(), id).cache())
                                            .map(client -> toEntity(approval, client, types)))
                                    .toList();
                        }));
    }

    private Flowable<AuthorizationDetailApproval> approvals(Domain domain, User user, String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return authorizationDetailApprovalService.findByDomainAndUser(domain, user.getFullId());
        }
        return authorizationDetailApprovalService.findByDomainAndUserAndClient(domain, user.getFullId(), clientId);
    }

    private static AuthorizationDetailApprovalEntity toEntity(AuthorizationDetailApproval approval, ApplicationEntity client,
                                                              Map<String, AuthorizationDetailType> types) {
        var entity = new AuthorizationDetailApprovalEntity(approval);
        entity.setClientEntity(client);
        if (entity.getType() != null && types.containsKey(entity.getType())) {
            entity.setTypeEntity(new AuthorizationDetailTypeEntity(types.get(entity.getType())));
        }
        return entity;
    }
}
