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
package io.gravitee.am.management.handlers.management.api.authentication.provider.security;

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class EndUserAuthentication implements Authentication {

    private final Object principal;
    private final Object credentials;
    private final AuthenticationContext context;

    public EndUserAuthentication(Object principal, Object credentials) {
        this(principal, credentials, null);
    }

    public EndUserAuthentication(Object principal, Object credentials, AuthenticationContext context) {
        this.principal = principal;
        this.credentials = credentials;
        this.context = context;
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public AuthenticationContext getContext() {
        return context;
    }

    @Override
    public String toString() {
        return principal.toString();
    }
}
