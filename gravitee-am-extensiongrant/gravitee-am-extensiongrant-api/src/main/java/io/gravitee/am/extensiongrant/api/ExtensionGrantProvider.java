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
package io.gravitee.am.extensiongrant.api;

import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.oauth2.request.TokenRequest;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ExtensionGrantProvider {

    /**
     * Grant OAuth2 access tokens by validating the assertion stored inside the incoming token request
     * @param tokenRequest tokenRequest token endpoint request
     * @return User representation of the assertion or null if no user is involved
     * @throws InvalidGrantException if the assertion is not valid
     */
    User grant(TokenRequest tokenRequest) throws InvalidGrantException;
}
