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
package io.gravitee.am.gateway.handler.common.jwt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.certificate.jwt.JWTBuilder;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.impl.JWTServiceImpl;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JWTServiceTest {

    @InjectMocks
    private JWTService jwtService = new JWTServiceImpl();

    @Mock
    private CertificateManager certificateManager;

    @Before
    public void setUp() {
        JWTBuilder rs256JWTBuilder = mock(JWTBuilder.class);
        JWTBuilder rs512JWTBuilder = mock(JWTBuilder.class);
        JWTBuilder defaultJWTBuilder = mock(JWTBuilder.class);
        JWTBuilder noneAlgBuilder = mock(JWTBuilder.class);
        when(rs256JWTBuilder.sign(any())).thenReturn("token_rs_256");
        when(rs512JWTBuilder.sign(any())).thenReturn("token_rs_512");
        when(defaultJWTBuilder.sign(any())).thenReturn("token_default");
        when(noneAlgBuilder.sign(any())).thenReturn("not_signed_jwt");

        io.gravitee.am.gateway.certificate.CertificateProvider rs256CertProvider = mock(
            io.gravitee.am.gateway.certificate.CertificateProvider.class
        );
        io.gravitee.am.gateway.certificate.CertificateProvider rs512CertProvider = mock(
            io.gravitee.am.gateway.certificate.CertificateProvider.class
        );
        io.gravitee.am.gateway.certificate.CertificateProvider defaultCertProvider = mock(
            io.gravitee.am.gateway.certificate.CertificateProvider.class
        );
        io.gravitee.am.gateway.certificate.CertificateProvider noneAlgCertProvider = mock(
            io.gravitee.am.gateway.certificate.CertificateProvider.class
        );
        when(rs256CertProvider.getJwtBuilder()).thenReturn(rs256JWTBuilder);
        when(rs512CertProvider.getJwtBuilder()).thenReturn(rs512JWTBuilder);
        when(defaultCertProvider.getJwtBuilder()).thenReturn(defaultJWTBuilder);
        when(noneAlgCertProvider.getJwtBuilder()).thenReturn(noneAlgBuilder);

        when(certificateManager.findByAlgorithm("unknown")).thenReturn(Maybe.empty());
        when(certificateManager.findByAlgorithm("RS512")).thenReturn(Maybe.just(rs512CertProvider));
        when(certificateManager.get(null)).thenReturn(Maybe.empty());
        when(certificateManager.get("notExistingId")).thenReturn(Maybe.empty());
        when(certificateManager.get("existingId")).thenReturn(Maybe.just(rs256CertProvider));
        when(certificateManager.defaultCertificateProvider()).thenReturn(defaultCertProvider);
        when(certificateManager.noneAlgorithmCertificateProvider()).thenReturn(noneAlgCertProvider);
    }

    @Test
    public void encode_noClientCertificate() {
        this.testEncode(null, "token_default");
    }

    @Test
    public void encode_noClientCertificateFound() {
        this.testEncode("notExistingId", "token_default");
    }

    @Test
    public void encode_clientCertificateFound() {
        this.testEncode("existingId", "token_rs_256");
    }

    private void testEncode(String clientCertificate, String expectedResult) {
        Client client = new Client();
        client.setCertificate(clientCertificate);

        TestObserver testObserver = jwtService.encode(new JWT(), client).test();
        testObserver.assertComplete();
        testObserver.assertValue(o -> o.equals(expectedResult));
    }

    @Test
    public void encodeUserinfo_withoutSignature() {
        this.testEncodeUserinfo(null, null, "not_signed_jwt");
    }

    @Test
    public void encodeUserinfo_noMatchingAlgorithm_noClientCertificate() {
        this.testEncodeUserinfo("unknown", null, "token_default");
    }

    @Test
    public void encodeUserinfo_noMatchingAlgorithm_noClientCertificateFound() {
        this.testEncodeUserinfo("unknown", "notExistingId", "token_default");
    }

    @Test
    public void encodeUserinfo_noMatchingAlgorithm_clientCertificateFound() {
        this.testEncodeUserinfo("unknown", "existingId", "token_rs_256");
    }

    @Test
    public void encodeUserinfo_matchingAlgorithm() {
        this.testEncodeUserinfo("RS512", null, "token_rs_512");
    }

    private void testEncodeUserinfo(String algorithm, String clientCertificate, String expectedResult) {
        Client client = new Client();
        client.setUserinfoSignedResponseAlg(algorithm);
        client.setCertificate(clientCertificate);

        TestObserver testObserver = jwtService.encodeUserinfo(new JWT(), client).test();
        testObserver.assertComplete();
        testObserver.assertValue(o -> o.equals(expectedResult));
    }
}
