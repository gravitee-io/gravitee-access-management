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
package io.gravitee.am.gateway.handler.common.authorizationengine;

import io.gravitee.am.authorizationengine.api.AuthorizationEngineProvider;
import io.gravitee.common.service.Service;
import io.reactivex.rxjava3.core.Maybe;

/**
 * @author GraviteeSource Team
 */
public interface AuthorizationEngineManager extends Service {

    /**
     * Gets an authorization engine provider associated with the current domain with a given engine identifier.
     * @param id Authorization engine identifier.
     * @return Provider instance; or empty if not found.
     */
    Maybe<AuthorizationEngineProvider> get(String id);

    /**
     * Gets the default authorization engine provider associated with the current domain.
     * @return Provider instance; or empty if not found.
     */
    Maybe<AuthorizationEngineProvider> getDefault();
}
