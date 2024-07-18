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
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.exception.UserNotFoundException;

import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

import static io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService.USER_ID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnCookieServiceTest {

    @Mock
    private JWTService jwtService;

    @Mock
    private UserService userService;

    @Mock
    private CertificateManager certificateManager;

    @Mock
    private CertificateProvider certificateProvider;

    @InjectMocks
    private WebAuthnCookieService webAuthnCookieService = new WebAuthnCookieService();

    @Before
    public void init() throws Exception {
        when(certificateManager.defaultCertificateProvider()).thenReturn(certificateProvider);
        webAuthnCookieService.afterPropertiesSet();
    }

    @Test
    public void shouldGenerateRememberDeviceCookieValue_nominal_case() {
        User user = mock(User.class);
        when(user.getId()).thenReturn("user-id");
        when(jwtService.encode(any(), eq(certificateProvider))).thenReturn(Single.just("cookieValue"));

        TestObserver<String> testObserver = webAuthnCookieService.generateRememberDeviceCookieValue(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue("cookieValue"::equals);
    }

    @Test
    public void shouldExtractUserIdFromRememberDeviceCookieValue_nominal_case() {
        when(jwtService.decodeAndVerify(anyString(), eq(certificateProvider), any())).thenReturn(Single.just(new JWT(Map.of(USER_ID, "userId"))));
        TestObserver<String> testObserver = webAuthnCookieService.extractUserIdFromRememberDeviceCookieValue("cookieValue").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(value -> value.equals("userId"));
    }

    @Test
    public void shouldVerifyRememberDeviceCookieValue_error() {
        when(jwtService.decodeAndVerify(anyString(), eq(certificateProvider), any())).thenReturn(Single.error(new IllegalArgumentException("invalid-token")));
        TestObserver<String> testObserver = webAuthnCookieService.extractUserIdFromRememberDeviceCookieValue("cookieValue").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNotComplete();
        testObserver.assertError(IllegalArgumentException.class);
    }

    @Test
    public void shouldExtractUser_nominal_case() {
        JWT jwt = new JWT();
        jwt.put("userId", "user-id");
        when(jwtService.decodeAndVerify(anyString(), eq(certificateProvider), any())).thenReturn(Single.just(jwt));
        when(userService.findById("user-id")).thenReturn(Maybe.just(new User()));
        TestObserver<User> testObserver = webAuthnCookieService.extractUserFromRememberDeviceCookieValue("cookieValue").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(Objects::nonNull);
    }

    @Test
    public void shouldExtractUser_no_user() {
        JWT jwt = new JWT();
        jwt.put("userId", "user-id");
        when(jwtService.decodeAndVerify(anyString(), eq(certificateProvider), any())).thenReturn(Single.just(jwt));
        when(userService.findById("user-id")).thenReturn(Maybe.empty());
        TestObserver<User> testObserver = webAuthnCookieService.extractUserFromRememberDeviceCookieValue("cookieValue").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);
    }

    @Test
    public void shouldExtractUser_error() {
        when(jwtService.decodeAndVerify(anyString(), eq(certificateProvider), any())).thenReturn(Single.error(new TechnicalException()));
        TestObserver<User> testObserver = webAuthnCookieService.extractUserFromRememberDeviceCookieValue("cookieValue").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNotComplete();
        testObserver.assertError(TechnicalException.class);
        verify(userService, never()).findById(anyString());
    }
}
