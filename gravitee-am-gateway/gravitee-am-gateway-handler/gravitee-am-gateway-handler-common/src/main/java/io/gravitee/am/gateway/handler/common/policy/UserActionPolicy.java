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
package io.gravitee.am.gateway.handler.common.policy;

import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Completable;

/**
 * Interface for User Action policies.
 * 
 * Implementations of this interface can execute custom user actions and optionally redirect the user.
 * Developers can extend {@link AbstractUserActionPolicy} to implement custom logic while maintaining
 * consistent handling of redirects and error management.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface UserActionPolicy {

    /**
     * Perform the actual user action logic
     * This method can be overridden in subclasses to implement specific business logic
     *
     * @param request the HTTP request
     * @param context the execution context
     * @throws Exception if an error occurs during the user action execution
     */
    Completable performUserAction(Request request, ExecutionContext context) throws Exception;

    /**
     * Return the action of the policy
     *
     * @return the action of the policy
     */
    String getAction();

    /**
     * Return the template used for the policy
     *
     * @return the template used for the policy
     */
    String getTemplate();
}
