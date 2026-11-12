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
package io.gravitee.am.service;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.dataplane.DomainDataPlaneCleanup;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

public interface AuthorizationDetailApprovalService extends DomainDataPlaneCleanup {

    Single<CursorPage<AuthorizationDetailApproval, AuthorizationDetailApprovalCursorRequest>> findByDomainAndUser(
            Domain domain, UserId userId, AuthorizationDetailApprovalCursorRequest cursor, int limit);

    Flowable<AuthorizationDetailApproval> findByDomainAndUserAndClient(Domain domain, UserId userId, String clientId);

    /**
     * Stores {@code approvals} decided by the user and audits the decision on the full {@code authorizationDetails}.
     */
    Completable saveConsent(Domain domain, Client client, User principal, AuthorizationDetails authorizationDetails,
                            List<AuthorizationDetailApproval> approvals, boolean approved);
}
