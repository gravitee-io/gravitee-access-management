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
package io.gravitee.am.gateway.handler.oidc.jwk;

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.gateway.handler.oauth2.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.oidc.jwk.impl.JWKSetServiceImpl;
import io.reactivex.Flowable;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JWKSetServiceTest {

    @InjectMocks
    private JWKSetService jwkSetService = new JWKSetServiceImpl();

    @Mock
    private CertificateManager certificateManager;

    @Test
    public void shouldGetJWKSet_singleKey() {
        io.gravitee.am.model.jose.JWK key = new io.gravitee.am.model.jose.RSAKey();
        key.setKid("my-test-key");

        CertificateProvider certificateProvider = mock(CertificateProvider.class);
        when(certificateProvider.keys()).thenReturn(Flowable.just(key));

        when(certificateManager.providers()).thenReturn(Collections.singletonList(certificateProvider));

        TestObserver<JWKSet> testObserver = jwkSetService.getKeys().test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(jwkSet -> "my-test-key".equals(jwkSet.getKeys().get(0).getKid()));
    }

    @Test
    public void shouldGetJWKSet_multipleKeys() {
        io.gravitee.am.model.jose.JWK key = new io.gravitee.am.model.jose.RSAKey();
        key.setKid("my-test-key");
        io.gravitee.am.model.jose.JWK key2 = new io.gravitee.am.model.jose.RSAKey();
        key.setKid("my-test-key-2");

        CertificateProvider certificateProvider = mock(CertificateProvider.class);
        when(certificateProvider.keys()).thenReturn(Flowable.just(key));
        CertificateProvider certificateProvider2 = mock(CertificateProvider.class);
        when(certificateProvider2.keys()).thenReturn(Flowable.just(key2));

        when(certificateManager.providers()).thenReturn(Arrays.asList(certificateProvider, certificateProvider2));

        TestObserver<JWKSet> testObserver = jwkSetService.getKeys().test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(jwkSet -> jwkSet.getKeys().size() == 2);
    }

    @Test
    public void shouldGetJWKSet_noCertificateProvider() {
        when(certificateManager.providers()).thenReturn(Collections.emptySet());

        TestObserver<JWKSet> testObserver = jwkSetService.getKeys().test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(jwkSet -> jwkSet.getKeys().isEmpty());
    }

}
