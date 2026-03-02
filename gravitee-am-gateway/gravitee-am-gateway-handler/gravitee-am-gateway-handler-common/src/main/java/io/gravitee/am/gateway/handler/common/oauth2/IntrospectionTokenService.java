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
package io.gravitee.am.gateway.handler.common.oauth2;

import io.gravitee.am.common.jwt.JWT;
import io.reactivex.rxjava3.core.Maybe;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IntrospectionTokenService {

    default Maybe<JWT> introspect(String token, boolean offlineVerification) {
        return introspect(token, offlineVerification, null).map(IntrospectionResult::jwt);
    }

    /**
     * Performs token introspection and returns both the verified JWT and
     * optional metadata coming from the persistence layer.
     */
    Maybe<IntrospectionResult> introspect(String token, boolean offlineVerification, String callerClientId);
}
