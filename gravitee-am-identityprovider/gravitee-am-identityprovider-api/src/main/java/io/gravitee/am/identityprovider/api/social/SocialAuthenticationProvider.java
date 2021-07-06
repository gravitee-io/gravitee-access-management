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
package io.gravitee.am.identityprovider.api.social;

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.common.Request;
import io.reactivex.Maybe;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface SocialAuthenticationProvider extends AuthenticationProvider {

    /**
     * Generate the signIn Url.
     *
     * @param redirectUri
     * @return
     * @Deprecated use the asyncSignInUrl instead
     */
    @Deprecated
    Request signInUrl(String redirectUri, String state);

    /**
     * Generate the signIn Url in asynchronous way
     * to avoid blocking thread when an http
     * call out is required to generate the url.
     *
     * @param redirectUri
     * @return
     */
    default Maybe<Request> asyncSignInUrl(String redirectUri, String state) {
        Request request = signInUrl(redirectUri, state);
        if (request != null) {
            return Maybe.just(request);
        } else {
            return Maybe.<Request>empty();
        }
    }

    /**
     * Get the logout endpoint related to the IdentityProvider (ex: "End Session Endpoint" in OIDC)
     * By default, do nothing since not all IdentityProvider implement this capabilities
     *
     * @return
     */
    default Maybe<Request> signOutUrl(Authentication authentication) {
        return Maybe.empty();
    }
}
