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
package io.gravitee.am.gateway.handler.vertx.handler.session;

import io.vertx.reactivex.ext.web.handler.SessionHandler;
import io.vertx.reactivex.ext.web.sstore.SessionStore;

/**
 * Override default Vert.x Session Handler to set session cookie path
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RxSessionHandler extends SessionHandler {

    private static final String DEFAULT_SESSION_COOKIE_PATH = "/";

    public RxSessionHandler(io.vertx.ext.web.handler.SessionHandler delegate) {
        super(delegate);
    }

    /**
     * Create a session handler
     * @param sessionStore the session store
     * @return the handler
     */
    public static SessionHandler create(SessionStore sessionStore) {
        SessionHandler ret = newInstance(new SessionHandlerImpl(DEFAULT_SESSION_COOKIE_NAME, DEFAULT_SESSION_COOKIE_PATH, DEFAULT_SESSION_TIMEOUT, DEFAULT_NAG_HTTPS, DEFAULT_COOKIE_SECURE_FLAG, DEFAULT_COOKIE_HTTP_ONLY_FLAG, DEFAULT_SESSIONID_MIN_LENGTH, sessionStore.getDelegate()));
        return ret;
    }

    public static SessionHandler newInstance(io.vertx.ext.web.handler.SessionHandler arg) {
        return arg != null ? new RxSessionHandler(arg) : null;
    }
}
