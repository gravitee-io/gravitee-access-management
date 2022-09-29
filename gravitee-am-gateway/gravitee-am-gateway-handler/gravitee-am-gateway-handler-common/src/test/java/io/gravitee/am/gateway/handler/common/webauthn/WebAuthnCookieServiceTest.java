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
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnCookieServiceTest {

    @Mock
    private JWTService jwtService;

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
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(cookieValue -> "cookieValue".equals(cookieValue));
    }

    @Test
    public void shouldVerifyRememberDeviceCookieValue_nominal_case() {
        when(jwtService.decodeAndVerify(anyString(), eq(certificateProvider))).thenReturn(Single.just(new JWT()));
        TestObserver<Void> testObserver = webAuthnCookieService.verifyRememberDeviceCookieValue("cookieValue").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void shouldVerifyRememberDeviceCookieValue_error() {
        when(jwtService.decodeAndVerify(anyString(), eq(certificateProvider))).thenReturn(Single.error(new IllegalArgumentException("invalid-token")));
        TestObserver<Void> testObserver = webAuthnCookieService.verifyRememberDeviceCookieValue("cookieValue").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNotComplete();
        testObserver.assertError(IllegalArgumentException.class);
    }
}
