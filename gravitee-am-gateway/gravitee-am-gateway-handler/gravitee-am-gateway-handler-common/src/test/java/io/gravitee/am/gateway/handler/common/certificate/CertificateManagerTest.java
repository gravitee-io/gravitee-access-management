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
package io.gravitee.am.gateway.handler.common.certificate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.certificate.impl.CertificateManagerImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.jsonwebtoken.Jwts;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashSet;

import static org.mockito.Mockito.*;
import static org.junit.Assert.assertEquals;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateManagerTest {

    @Spy
    @InjectMocks
    private CertificateManager certificateManager = new CertificateManagerImpl();

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private Domain domain;

    @Before
    public void setUp() {
        CertificateProvider rs256CertificateProvider = mock(CertificateProvider.class);
        CertificateProvider rs512CertificateProvider = mock(CertificateProvider.class);
        when(rs256CertificateProvider.signatureAlgorithm()).thenReturn("RS256");
        when(rs512CertificateProvider.signatureAlgorithm()).thenReturn("RS512");

        io.gravitee.am.gateway.handler.common.certificate.CertificateProvider rs256CertProvider =
                mock(io.gravitee.am.gateway.handler.common.certificate.CertificateProvider.class);
        io.gravitee.am.gateway.handler.common.certificate.CertificateProvider rs512CertProvider =
                mock(io.gravitee.am.gateway.handler.common.certificate.CertificateProvider.class);
        when(rs256CertProvider.getProvider()).thenReturn(rs256CertificateProvider);
        when(rs512CertProvider.getProvider()).thenReturn(rs512CertificateProvider);
        doReturn(Arrays.asList(rs256CertProvider,rs512CertProvider)).when(certificateManager).providers();

        doReturn(Single.just(new HashSet())).when(certificateRepository).findAll();
        ReflectionTestUtils.setField(certificateManager,"signingKeySecret","s3cR3t4grAv1t3310AMS1g1ingDftK3y");
        ReflectionTestUtils.setField(certificateManager,"signingKeyId","default-gravitee-AM-key");
        ReflectionTestUtils.setField(certificateManager,"objectMapper",new ObjectMapper());

        when(domain.getName()).thenReturn("unit-testing-domain");
        ((CertificateManagerImpl)certificateManager).afterPropertiesSet();
    }

    @Test
    public void findByAlgorithm_nullAlgorithm() {
        TestObserver testObserver = certificateManager.findByAlgorithm(null).test();
        testObserver
                .assertComplete()
                .assertNoValues();

    }

    @Test
    public void findByAlgorithm_emptyAlgorithm() {
        TestObserver testObserver = certificateManager.findByAlgorithm("").test();
        testObserver.assertComplete();
        testObserver
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void findByAlgorithm_unknownAlgorithm() {
        TestObserver testObserver = certificateManager.findByAlgorithm("unknown").test();
        testObserver.assertComplete();
        testObserver
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void findByAlgorithm_foundAlgorithm() {
        TestObserver testObserver = certificateManager.findByAlgorithm("RS512").test();
        testObserver.assertComplete();
        testObserver
                .assertComplete()
                .assertValue(o -> "RS512".equals(
                        ((io.gravitee.am.gateway.handler.common.certificate.CertificateProvider)o)
                                .getProvider()
                                .signatureAlgorithm()
                ));
    }

    @Test
    public void noneAlgorithmCertificateProvider_nominalCase() {
        JWT jwt = new JWT();
        jwt.setIss("iss");
        jwt.setSub("sub");

        assertEquals(
                "non matching jwt with none algorithm",
                "eyJhbGciOiJub25lIn0.eyJzdWIiOiJzdWIiLCJpc3MiOiJpc3MifQ.",
                certificateManager.noneAlgorithmCertificateProvider().getJwtBuilder().sign(jwt)
        );
    }

    @Test(expected = UnsupportedOperationException.class)
    public void noneAlgorithmCertificateProvider_accessToKeys() {
        certificateManager.noneAlgorithmCertificateProvider().getProvider().keys();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void noneAlgorithmCertificateProvider_accessToKey() {
        certificateManager.noneAlgorithmCertificateProvider().getProvider().key();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void noneAlgorithmCertificateProvider_accessToPublicKey() {
        certificateManager.noneAlgorithmCertificateProvider().getProvider().publicKey();
    }

    @Test
    public void noneAlgorithmCertificateProvider_accessToProviderProperty() {
        assertEquals("none",certificateManager.noneAlgorithmCertificateProvider().getProvider().signatureAlgorithm());
        assertEquals("none",certificateManager.noneAlgorithmCertificateProvider().getProvider().certificateMetadata().getMetadata().get("digestAlgorithmName"));
    }

    @Test
    public void defaultCertificateProvider_nominalCase() {
        JWT jwt = new JWT();
        jwt.setIss("iss");
        jwt.setSub("sub");

        assertEquals(
                "non matching jwt with default certificateProvider",
                "eyJraWQiOiJkZWZhdWx0LWdyYXZpdGVlLUFNLWtleSIsImFsZyI6IkhTMjU2In0.eyJzdWIiOiJzdWIiLCJpc3MiOiJpc3MifQ.ih3-kQgeGAQrL2H8pZMy979gVP0HWOH7p8-_7Ar0Lbs",
                certificateManager.defaultCertificateProvider().getJwtBuilder().sign(jwt)
        );
    }
}
