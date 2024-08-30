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
package io.gravitee.am.gateway.handler.users.service;

import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Set;

/**
 * Manages gateway operations on users within a single domain
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DomainUserConsentFacade {

    Single<Set<ScopeApproval>> consents(UserId userId);

    Single<Set<ScopeApproval>> consents(UserId userId, String clientId);

    Maybe<ScopeApproval> consent(String consentId);

    Completable revokeConsent(UserId userId, String consentId, io.gravitee.am.identityprovider.api.User principal);

    Completable revokeConsents(UserId userId, io.gravitee.am.identityprovider.api.User principal);

    Completable revokeConsents(UserId userId, String clientId, io.gravitee.am.identityprovider.api.User principal);

}
