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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.AuthorizationDetailApprovalEntity;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public interface AuthorizationDetailApprovalAdapter {

    /**
     * Fails with {@link io.gravitee.am.service.exception.UserNotFoundException} when the domain has no such user.
     */
    Single<CursorPage<AuthorizationDetailApprovalEntity, AuthorizationDetailApprovalCursorRequest>> getUserConsents(
            Domain domain, String userId, AuthorizationDetailApprovalCursorRequest cursor, int limit);

    /**
     * Revokes the user's consents for {@code clientId}, or for every client when it is empty.
     */
    Completable revokeUserConsents(Domain domain, String userId, String clientId, User authenticatedUser);

    Completable revokeUserConsent(Domain domain, String userId, String consentId, User authenticatedUser);
}
