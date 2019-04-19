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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.handler;

import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.impl.ClientAssertionAuthHandlerImpl;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.reactivex.ext.web.handler.AuthHandler;

/**
 * <pre>
 * Oauth2 enable different kind of client authentication, here we focus on Client assertion (RFC 7521)
 * Specification is available <a href="https://tools.ietf.org/html/rfc7521.">here</a>
 * And more precisely the client authentication define <a href="https://tools.ietf.org/html/rfc7521#section-4.2">here</a>
 * </pre>
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public interface ClientAssertionAuthHandler {

    /**
     * Create an oauth2 client auth handler
     *
     * @param authProvider  the auth provider to use
     * @return the auth handler
     */
    static AuthHandler create(AuthProvider authProvider) {
        return AuthHandler.newInstance(new ClientAssertionAuthHandlerImpl(authProvider));
    }
}
