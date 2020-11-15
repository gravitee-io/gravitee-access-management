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
package io.gravitee.am.gateway.handler.root.resources.auth.handler;

import io.gravitee.am.gateway.handler.root.resources.auth.handler.impl.SocialAuthHandlerImpl;
import io.gravitee.am.gateway.handler.root.resources.auth.provider.SocialAuthenticationProvider;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface SocialAuthHandler extends Handler<RoutingContext> {

    /**
     * Create authentication handler for social/oauth2 authentication
     *
     * @param authProvider  the auth provider to use
     * @return the auth handler
     */
    static SocialAuthHandler create(SocialAuthenticationProvider authProvider) {
        return new SocialAuthHandlerImpl(authProvider);
    }
}
