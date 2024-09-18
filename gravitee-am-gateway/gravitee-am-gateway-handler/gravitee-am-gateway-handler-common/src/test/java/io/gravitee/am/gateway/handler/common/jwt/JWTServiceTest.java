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

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.certificate.api.DefaultKey;
import io.gravitee.am.certificate.api.Key;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.impl.JWTServiceImpl;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.crypto.KeyGenerator;
import java.security.KeyPairGenerator;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        var rs256CertProvider = mockCertProvider(constantJwtBuilder("token_rs_256"));
        var rs512CertProvider = mockCertProvider(constantJwtBuilder("token_rs_512"));
        var defaultCertProvider = mockCertProvider(constantJwtBuilder("token_default"));
        var noneAlgCertProvider = mockCertProvider(constantJwtBuilder("not_signed_jwt"));

        when(certificateManager.findByAlgorithm("unknown")).thenReturn(Maybe.empty());
        when(certificateManager.findByAlgorithm("RS512")).thenReturn(Maybe.just(rs512CertProvider));
        when(certificateManager.get(null)).thenReturn(Maybe.empty());
        when(certificateManager.get("notExistingId")).thenReturn(Maybe.empty());
        when(certificateManager.get("existingId")).thenReturn(Maybe.just(rs256CertProvider));
        when(certificateManager.defaultCertificateProvider()).thenReturn(defaultCertProvider);
        when(certificateManager.noneAlgorithmCertificateProvider()).thenReturn(noneAlgCertProvider);
    }

    private JWTBuilder constantJwtBuilder(String theSignedValue) {
        return x -> theSignedValue;
    }

    private io.gravitee.am.gateway.certificate.CertificateProvider mockCertProvider(JWTBuilder jwtBuilder) {
        return mockCertProvider(jwtBuilder, generateKey("HMACSHA256")); // that's the default cert provider's key type
    }

    private io.gravitee.am.gateway.certificate.CertificateProvider mockCertProvider(JWTBuilder jwtBuilder, Key key) {
        var actualProvider = mock(CertificateProvider.class);
        when(actualProvider.key()).thenReturn(Single.just(key));

        var theProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        when(theProvider.getJwtBuilder()).thenReturn(jwtBuilder);
        when(theProvider.getProvider()).thenReturn(actualProvider);
        return theProvider;
    }

    @SneakyThrows
    private Key generateKey(String algorithm) {
        var keygen = KeyGenerator.getInstance(algorithm);
        keygen.init(256);
        var theKey = keygen.generateKey();
        return new DefaultKey("hmacsha256-test", theKey);
    }

    @SneakyThrows
    private Key generateKeyPair(String algorithm) {
        var keyPairGen = KeyPairGenerator.getInstance(algorithm);
        keyPairGen.initialize(2048);
        var rsaKeyPair = keyPairGen.generateKeyPair();
        return new DefaultKey("rsa-test", rsaKeyPair.getPrivate());
    }

    @Test
    public void encode_withEncryption_hmacShaKey() {
        var key = generateKey("HMACSHA256");
        var jwt = new JWT(Map.of("ecv", "to-encrypt",
                "normalclaim", "lorem-ipsum"));
        jwtService.encode(jwt, mockCertProvider(constantJwtBuilder("with-encryption"), key))
                .test()
                .assertComplete()
                .assertValue("with-encryption");
    }

    @Test
    public void encode_withEncryption_rsaKeyPair() {
        var key = generateKeyPair("RSA");
        var jwt = new JWT(Map.of("ecv", "to-encrypte",
                "normalclaim", "lorem-ipsum"));
        jwtService.encode(jwt, mockCertProvider(constantJwtBuilder("with-encryption"), key))
                .test()
                .assertComplete()
                .assertValue("with-encryption");
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

        jwtService.encode(new JWT(), client).test()
                .assertComplete()
                .assertValue(o -> o.equals(expectedResult));
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

        jwtService.encodeUserinfo(new JWT(), client)
                .test()
                .assertComplete()
                .assertValue(o -> o.equals(expectedResult));
    }
}
