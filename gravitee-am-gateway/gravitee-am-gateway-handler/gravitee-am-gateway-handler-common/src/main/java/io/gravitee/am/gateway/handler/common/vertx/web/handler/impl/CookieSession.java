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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.reactivex.rxjava3.core.Single;
import io.vertx.ext.auth.VertxContextPRNG;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.sstore.AbstractSession;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;

import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.SESSION;
import static io.vertx.rxjava3.ext.web.sstore.cookie.CookieSessionStore.DEFAULT_SESSIONID_LENGTH;

/**
 * Manage the session data using JWT cookie.
 *
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CookieSession extends AbstractSession {

    private final JWTService jwtService;
    private final CertificateProvider certificateProvider;
    private Date lastLogin;

    public CookieSession(JWTService jwtService, CertificateProvider certificateProvider, long timeout) {
        super(VertxContextPRNG.current(), timeout, DEFAULT_SESSIONID_LENGTH);
        this.jwtService = jwtService;
        this.certificateProvider = certificateProvider;
        this.setTimeout(timeout);
        this.lastLogin = new Date();
    }

    @Override
    public String value() {
        JWT jwt = new JWT(this.data());
        jwt.setExp((System.currentTimeMillis() + this.timeout()) / 1000);
        return this.jwtService.encode(jwt, certificateProvider).blockingGet();
    }

    @Override
    public Session regenerateId() {
        return this;
    }

    public Date lastLogin() {
        return lastLogin;
    }

    Session putUserId(Object obj) {
        super.put(CookieSessionHandler.USER_ID_KEY, obj);
        return this;
    }

    @Override
    public Session put(String key, Object obj) {
        // Do not allow to push a userId key to avoid session compromise
        if (key.equalsIgnoreCase(CookieSessionHandler.USER_ID_KEY)) {
            throw new IllegalArgumentException(CookieSessionHandler.USER_ID_KEY + " can not be used as a session key!");
        }

        return super.put(key, obj);
    }

    protected Single<CookieSession> setValue(String payload) {

        if (StringUtils.isEmpty(payload)) {
            setData(new HashMap<>());
        }

        return this.jwtService.decodeAndVerify(payload, certificateProvider, SESSION)
                .doOnSuccess(jwt -> {
                    this.lastLogin = new Date(jwt.getExp() * 1000 - this.timeout());
                    this.setData(jwt);
                    var jwtXsrfToken = jwt.get(ConstantKeys.X_XSRF_TOKEN);
                    if (jwtXsrfToken != null) {
                        var xsrfToken = jwtXsrfToken.toString();
                        this.setId(xsrfToken.substring(0, xsrfToken.indexOf('/')));
                    }
                })
                .map(jwt -> this);
    }
}
