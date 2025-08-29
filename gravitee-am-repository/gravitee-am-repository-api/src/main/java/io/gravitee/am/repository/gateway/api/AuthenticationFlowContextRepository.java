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
package io.gravitee.am.repository.gateway.api;

import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.repository.common.ExpiredDataSweeper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Repository to store information between different phases of authentication flow.
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AuthenticationFlowContextRepository extends ExpiredDataSweeper {
    Maybe<AuthenticationFlowContext> findById(String id);
    /**
     * Find last context data for given sessionId
     *
     * @param transactionId transactionId id
     * @return
     */
    Maybe<AuthenticationFlowContext> findLastByTransactionId(String transactionId);
    /**
     * Find all contexts data for given sessionId
     *
     * @param transactionId transactionId id
     * @return
     */
    Flowable<AuthenticationFlowContext> findByTransactionId(String transactionId);

    /**
     * Create authentication context
     * @param
     * @return
     */
    Single<AuthenticationFlowContext> create(AuthenticationFlowContext context);

    /**
     * Create authentication context
     * @param
     * @return
     */
    Single<AuthenticationFlowContext> replace(AuthenticationFlowContext context);

    /**
     * Delete all context for given transactionId Id
     * @param transactionId
     * @return acknowledge of the operation
     */
    Completable delete(String transactionId);

    /**
     * Delete context for given transactionId Id and specific version
     * @param transactionId
     * @param version
     * @return acknowledge of the operation
     */
    Completable delete(String transactionId, int version);

}
