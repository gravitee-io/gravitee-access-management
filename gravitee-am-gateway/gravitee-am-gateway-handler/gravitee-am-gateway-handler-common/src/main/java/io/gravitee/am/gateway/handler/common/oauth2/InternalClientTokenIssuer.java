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

import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;

/**
 * Issues client_credentials access tokens for clients of the current domain, without firing token flows.
 */
public interface InternalClientTokenIssuer {

    /**
     * Issues an access token for the client with its default scopes, issued by the domain gateway url.
     */
    Single<IssuedToken> issue(Client client);

    record IssuedToken(String value, long expiresIn) {
    }
}
