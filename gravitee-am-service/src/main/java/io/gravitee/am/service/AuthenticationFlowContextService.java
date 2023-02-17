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

import io.gravitee.am.model.AuthenticationFlowContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AuthenticationFlowContextService {

    Maybe<AuthenticationFlowContext> loadContext(final String transactionId, final int expectedVersion);

    Maybe<AuthenticationFlowContext> removeContext(final String transactionId, final int expectedVersion);

    /**
     * Update the AuthFlowContext in the database. This method will manage the increment of the context version.
     *
     * @param context
     * @return
     */
    Single<AuthenticationFlowContext> updateContext(final AuthenticationFlowContext context);

    Completable clearContext(final String transactionId);
}
