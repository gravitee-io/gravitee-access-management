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
package io.gravitee.am.gateway.handler.common.webauthn;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.User;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * WebAuthn cookie service used mainly to determine if the device is already enrolled or not,
 * and prompt the user for enrollment by using HTTP cookie
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnCookieService implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebAuthnCookieService.class);
    private static final String DEFAULT_COOKIE_NAME = "GRAVITEE_AM_DEVICE_RECOGNITION";
    private static final long DEFAULT_SESSION_TIMEOUT = (long) 365 * 24 * 60 * 60 * 1000; // a year
    private static final String USER_ID = "userId";

    @Value("${passwordless.rememberDevice.cookie.name:" + DEFAULT_COOKIE_NAME + "}")
    private String rememberDeviceCookieName;

    @Value("${passwordless.rememberDevice.cookie.timeout:" + DEFAULT_SESSION_TIMEOUT + "}")
    private long rememberDeviceCookieTimeout;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private CertificateManager certificateManager;

    private CertificateProvider certificateProvider;

    @Override
    public void afterPropertiesSet() throws Exception {
        certificateProvider = certificateManager.defaultCertificateProvider();
    }

    public String getRememberDeviceCookieName() {
        return rememberDeviceCookieName;
    }

    public long getRememberDeviceCookieTimeout() {
        return rememberDeviceCookieTimeout;
    }

    public Single<String> generateRememberDeviceCookieValue(User user) {
        final JWT jwt = new JWT();
        jwt.setIat(System.currentTimeMillis() / 1000);
        jwt.put(USER_ID, user.getId());
        // do we need to store more data ??
        return jwtService.encode(jwt, certificateProvider);
    }

    public Completable verifyRememberDeviceCookieValue(String cookieValue) {
        return jwtService.decodeAndVerify(cookieValue, certificateProvider)
                .ignoreElement()
                .onErrorResumeNext(throwable -> {
                    LOGGER.error("An error has occurred when parsing WebAuthn cookie {}", cookieValue, throwable);
                    return Completable.error(throwable);
                });
    }

}
