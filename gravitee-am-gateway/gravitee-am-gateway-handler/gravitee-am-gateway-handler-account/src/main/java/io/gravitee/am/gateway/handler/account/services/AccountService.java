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
package io.gravitee.am.gateway.handler.account.services;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.account.model.UpdateUsername;
import io.gravitee.am.gateway.handler.root.service.response.ResetPasswordResponse;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AccountService {

    Maybe<User> getBySub(JWT token);

    Maybe<User> getByUserId(String userId);

    Single<Page<Audit>> getActivity(User user, AuditReportableCriteria criteria, int page, int size);

    Single<User> update(User user);

    Single<User> updateUsername(User user, UpdateUsername newUsername, io.gravitee.am.identityprovider.api.User principal);

    Single<ResetPasswordResponse> resetPassword(User user, Client client, String password, io.gravitee.am.identityprovider.api.User principal, Optional<String> oldPassword);

    Single<User> upsertFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal);

    Completable removeFactor(String userId, String factorId, io.gravitee.am.identityprovider.api.User principal);

    Single<List<Factor>> getFactors(String domain);

    Maybe<Factor> getFactor(String id);

    Single<List<Credential>> getWebAuthnCredentials(User user);

    Single<Credential> getWebAuthnCredential(String id);

    Completable removeWebAuthnCredential(String userId, String id, io.gravitee.am.identityprovider.api.User principal);

    Single<Credential> updateWebAuthnCredential(String userId, String id, String deviceName, io.gravitee.am.identityprovider.api.User principal);

    Single<List<ScopeApproval>> getConsentList(User user, Client client);

    Single<ScopeApproval> getConsent(String id);

    Completable removeConsent(String userId, String consentId, io.gravitee.am.identityprovider.api.User principal);
}
