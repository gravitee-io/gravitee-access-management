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
package io.gravitee.am.gateway.handler.oauth2.service.token;

import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.common.service.Service;
import io.reactivex.rxjava3.core.Completable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface TokenManager extends Service {

    /**
     * Store an access token and, when provided, its refresh token in a single write.
     * <p>
     * The write is not atomic on every backend: on error one of the tokens may already be stored.
     *
     * @param accessToken the access token to store
     * @param refreshToken the refresh token to store, may be null
     * @return acknowledge of the operation
     */
    Completable storeTokens(AccessToken accessToken, RefreshToken refreshToken);
}
