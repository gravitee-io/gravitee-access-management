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
package io.gravitee.am.repository.oauth2.api;

import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AuthorizationCodeRepository {
    /**
     * Store an authorization code.
     *
     * @param authorizationCode The authorization code.
     * @return
     */
    Single<AuthorizationCode> create(AuthorizationCode authorizationCode);

    /**
     * Look for an {@link AuthorizationCode} by id and delete it.
     *
     * @param id The id to consume.
     */
    Maybe<AuthorizationCode> delete(String id);

    /**
     * Find an {@link AuthorizationCode} by its code.
     *
     * @param code The authorization code.
     * @return
     */
    Maybe<AuthorizationCode> findByCode(String code);
}
