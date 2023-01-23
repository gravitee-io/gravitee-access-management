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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnEnforcePasswordHandlerTest extends RxWebTestBase {

    @Mock
    private WebAuthnCookieService webAuthnCookieService;

    @Mock
    private Domain domain;
    @InjectMocks
    private WebAuthnEnforcePasswordHandler webAuthnEnforcePasswordHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        webAuthnEnforcePasswordHandler = new WebAuthnEnforcePasswordHandler(domain, webAuthnCookieService);
        router.route(HttpMethod.GET, "/webauthn/login")
                .handler(webAuthnEnforcePasswordHandler)
                .failureHandler(rc -> rc.response().setStatusCode(rc.statusCode()).end());
    }

    @Test
    public void shouldNotAddEnforcePassword_option_disabled() throws Exception {
        router.route(HttpMethod.GET, "/webauthn/login")
                .order(-1)
                .handler(rc -> {
                    // set application
                    Client client = new Client();
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });

        shouldNotAddEnforcePassword();
    }

    @Test
    public void shouldNotAddEnforcePassword_option_disabled_2() throws Exception {
        router.route(HttpMethod.GET, "/webauthn/login")
                .order(-1)
                .handler(rc -> {
                    // set application
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(false);
                    Client client = new Client();
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });
        shouldNotAddEnforcePassword();
    }

    @Test
    public void shouldNotAddEnforcePassword_option_disabled_3() throws Exception {
        router.route(HttpMethod.GET, "/webauthn/login")
                .order(-1)
                .handler(rc -> {
                    // set application
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(true);
                    loginSettings.setPasswordlessEnforcePasswordEnabled(false);
                    Client client = new Client();
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });
        shouldNotAddEnforcePassword();
    }

    @Test
    public void shouldNotAddEnforcePassword_option_disabled_4() throws Exception {
        router.route(HttpMethod.GET, "/webauthn/login")
                .order(-1)
                .handler(rc -> {
                    // set application
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(true);
                    loginSettings.setPasswordlessEnforcePasswordEnabled(true);
                    loginSettings.setPasswordlessEnforcePasswordMaxAge(null);;
                    Client client = new Client();
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });
        shouldNotAddEnforcePassword();
    }

    @Test
    public void shouldNotAddEnforcePassword_option_enabled_no_cookie() throws Exception {
        final String cookieName = "INVALID_GRAVITEE_AM_DEVICE_RECOGNITION";
        when(webAuthnCookieService.getRememberDeviceCookieName()).thenReturn(cookieName);

        router.route(HttpMethod.GET, "/webauthn/login")
                .order(-1)
                .handler(rc -> {
                    // set application
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(true);
                    loginSettings.setPasswordlessEnforcePasswordEnabled(true);
                    loginSettings.setPasswordlessEnforcePasswordMaxAge(30);;
                    Client client = new Client();
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });
        shouldNotAddEnforcePassword();
    }

    @Test
    public void shouldNotAddEnforcePassword_option_enabled_technical_exception() throws Exception {
        final String cookieName = "GRAVITEE_AM_DEVICE_RECOGNITION";
        when(webAuthnCookieService.getRememberDeviceCookieName()).thenReturn(cookieName);
        when(webAuthnCookieService.extractUserFromRememberDeviceCookieValue(anyString())).thenReturn(Single.error(new TechnicalException()));
        router.route(HttpMethod.GET, "/webauthn/login")
                .order(-1)
                .handler(rc -> {
                    // set application
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(true);
                    loginSettings.setPasswordlessEnforcePasswordEnabled(true);
                    loginSettings.setPasswordlessEnforcePasswordMaxAge(30);;
                    Client client = new Client();
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });
        shouldNotAddEnforcePassword();
    }

    @Test
    public void shouldNotAddEnforcePassword_option_enabled_account_valid() throws Exception {
        final String cookieName = "GRAVITEE_AM_DEVICE_RECOGNITION";
        final String cookieValue = "cookie-value";
        final User user = new User();
        final Date lastLogin = new Date(Instant.now().minus(15, ChronoUnit.SECONDS).toEpochMilli());
        user.setLastLoginWithCredentials(lastLogin);
        when(webAuthnCookieService.getRememberDeviceCookieName()).thenReturn(cookieName);
        when(webAuthnCookieService.extractUserFromRememberDeviceCookieValue(cookieValue)).thenReturn(Single.just(user));
        router.route(HttpMethod.GET, "/webauthn/login")
                .order(-1)
                .handler(rc -> {
                    // set application
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(true);
                    loginSettings.setPasswordlessEnforcePasswordEnabled(true);
                    loginSettings.setPasswordlessEnforcePasswordMaxAge(30);;
                    Client client = new Client();
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });
        shouldNotAddEnforcePassword();
    }

    @Test
    public void shouldNotAddEnforcePassword_option_enabled_account_invalid() throws Exception {
        final String cookieName = "GRAVITEE_AM_DEVICE_RECOGNITION";
        final String cookieValue = "cookie-value";
        final User user = new User();
        final Date lastLogin = new Date(Instant.now().minus(45, ChronoUnit.DAYS).toEpochMilli());
        user.setLastLoginWithCredentials(lastLogin);
        when(webAuthnCookieService.getRememberDeviceCookieName()).thenReturn(cookieName);
        when(webAuthnCookieService.extractUserFromRememberDeviceCookieValue(cookieValue)).thenReturn(Single.just(user));
        router.route(HttpMethod.GET, "/webauthn/login")
                .order(-1)
                .handler(rc -> {
                    // set application
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(true);
                    loginSettings.setPasswordlessEnforcePasswordEnabled(true);
                    loginSettings.setPasswordlessEnforcePasswordMaxAge(30);;
                    Client client = new Client();
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });
        shouldAddEnforcePassword();
    }


    private void shouldNotAddEnforcePassword() throws Exception {
        router.route(HttpMethod.GET, "/webauthn/login")
                .handler(rc -> {
                    Assert.assertNull(rc.get(ConstantKeys.PASSWORDLESS_ENFORCE_PASSWORD));
                    rc.end();
                });
        testRequest();
    }

    private void shouldAddEnforcePassword() throws Exception {
        router.route(HttpMethod.GET, "/webauthn/login")
                .handler(rc -> {
                    Assert.assertNotNull(rc.get(ConstantKeys.PASSWORDLESS_ENFORCE_PASSWORD));
                    Assert.assertTrue(rc.get(ConstantKeys.PASSWORDLESS_ENFORCE_PASSWORD));
                    rc.end();
                });
        testRequest();
    }

    private void testRequest() throws Exception {
        testRequestWithCookies(
                HttpMethod.GET,
                "/webauthn/login",
                "GRAVITEE_AM_DEVICE_RECOGNITION=cookie-value",
                200, "OK");
    }
}
