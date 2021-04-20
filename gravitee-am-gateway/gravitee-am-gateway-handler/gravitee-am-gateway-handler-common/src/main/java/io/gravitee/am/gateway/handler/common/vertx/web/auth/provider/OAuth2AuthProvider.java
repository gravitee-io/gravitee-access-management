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
package io.gravitee.am.gateway.handler.common.vertx.web.auth.provider;

import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthResponse;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * OAuth 2.0 Auth Provider (decode and verify OAuth 2.0 JWT token)
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface OAuth2AuthProvider {
    default void decodeToken(String token, Handler<AsyncResult<OAuth2AuthResponse>> handler) {
        decodeToken(token, false, handler);
    }

    void decodeToken(String token, boolean offlineVerification, Handler<AsyncResult<OAuth2AuthResponse>> handler);
}
