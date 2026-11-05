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
package io.gravitee.am.gateway.handler.oauth2.service.consent;

import io.gravitee.am.model.User;
import io.gravitee.am.model.oauth2.AuthorizationDetail;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * User consent to the authorization details (RFC 9396) of an authorization request. The user approves or rejects
 * the whole set.
 */
public interface AuthorizationDetailsConsentService {

    /**
     * The requested details the user has to consent to: all of them with {@code prompt=consent}, none when the
     * client skips user approval, otherwise those not covered by an approval the user gave earlier.
     */
    Single<List<AuthorizationDetail>> getDetailsToPresent(Client client, User user, AuthorizationDetails requested, boolean promptConsent);

    Completable saveConsent(Client client, User user, io.gravitee.am.identityprovider.api.User principal,
                            AuthorizationDetails requested, List<AuthorizationDetail> presented, boolean approved);
}
