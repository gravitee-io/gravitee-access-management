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

package io.gravitee.am.gateway.handler.common.service;


import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.model.account.AccountSettings;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface LoginAttemptGatewayService {
    Completable loginSucceeded(Domain domain, LoginAttemptCriteria criteria);

    Single<LoginAttempt> loginFailed(Domain domain, LoginAttemptCriteria criteria, AccountSettings accountSettings);

    default Completable reset(Domain domain, LoginAttemptCriteria criteria) {
        return loginSucceeded(domain, criteria);
    }

    Maybe<LoginAttempt> checkAccount(Domain domain, LoginAttemptCriteria criteria, AccountSettings accountSettings);

    Maybe<LoginAttempt> findById(Domain domain, String id);
}
