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
import io.gravitee.am.common.exception.oauth2.TemporarilyUnavailableException;
import io.gravitee.am.common.jwt.Claims;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.impl.JWTServiceImpl;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.crypto.KeyGenerator;
import java.security.KeyPairGenerator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JWTServiceTest {

    private JWTService jwtService;

    @Mock
    private CertificateManager certificateManager;

    @Before
    public void setUp() {
        jwtService = new JWTServiceImpl(certificateManager, new ObjectMapper(), true);
        var rs256CertProvider = mockCertProvider(mockJwtBuilder("token_rs_256"));
        var rs512CertProvider = mockCertProvider(mockJwtBuilder("token_rs_512"));
        var defaultCertProvider = mockCertProvider(mockJwtBuilder("token_default"));
        var noneAlgCertProvider = mockCertProvider(mockJwtBuilder("not_signed_jwt"));

        when(certificateManager.findByAlgorithm("unknown")).thenReturn(Maybe.empty());
        when(certificateManager.findByAlgorithm("RS512")).thenReturn(Maybe.just(rs512CertProvider));
        when(certificateManager.get("notExistingId")).thenReturn(Maybe.empty());
        when(certificateManager.get("existingId")).thenReturn(Maybe.just(rs256CertProvider));
        when(certificateManager.getClientCertificateProvider(any(), anyBoolean())).thenCallRealMethod();
        when(certificateManager.defaultCertificateProvider()).thenReturn(defaultCertProvider);
        when(certificateManager.noneAlgorithmCertificateProvider()).thenReturn(noneAlgCertProvider);
    }

    private JWTBuilder mockJwtBuilder(String theSignedValue) {
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
        return new DefaultKey("rsa-test", rsaKeyPair);
    }

    @Test
    public void encode_withEncryption_hmacShaKey() throws Exception {
        var key = generateKey("HMACSHA256");
        var jwt = new JWT(Map.of("ecv", "to-encrypt",
                "normalclaim", "lorem-ipsum"));
        var test = jwtService.encode(jwt, mockCertProvider(mockJwtBuilder("with-encryption"), key)).test();
        test.await(10, TimeUnit.SECONDS);
        test.assertComplete().assertValue("with-encryption");
    }

    @Test
    public void encode_withEncryption_notRealKey() {
        var key = new DefaultKey("not-a-key", 2137);
        var jwt = new JWT(Map.of("ecv", "to-encrypt",
                "normalclaim", "lorem-ipsum"));
        jwtService.encode(jwt, mockCertProvider(mockJwtBuilder("with-encryption"), key))
                .test()
                .assertError(IllegalArgumentException.class);
    }

    @Test
    public void encode_withEncryption_rsaKeyPair() throws Exception {
        var key = generateKeyPair("RSA");
        var jwt = new JWT(Map.of(Claims.ENCRYPTED_CODE_VERIFIER, "value-to-encrypt",
                "normalclaim", "lorem-ipsum"));
        var test = jwtService.encode(jwt, mockCertProvider(mockJwtBuilder("with-encryption"), key)).test();

        test.await(10, TimeUnit.SECONDS);
        test.assertComplete().assertValue("with-encryption");
    }

    @Test
    public void encode_noClientCertificate() throws Exception {
        this.testEncode(null, "token_default");
    }

    @Test
    public void encode_noClientCertificateFound() throws Exception  {
        this.testEncode("notExistingId", "token_default");
    }

    @Test
    public void encode_clientCertificateFound() throws Exception  {
        this.testEncode("existingId", "token_rs_256");
    }

    private void testEncode(String clientCertificate, String expectedResult) throws Exception {
        Client client = new Client();
        client.setCertificate(clientCertificate);

        var test = jwtService.encode(new JWT(), client).test();
        test.await(10, TimeUnit.SECONDS);
        test.assertComplete().assertValue(o -> o.equals(expectedResult));
    }

    @Test
    public void encodeUserinfo_withoutSignature() throws Exception  {
        this.testEncodeUserinfo(null, null, "not_signed_jwt");
    }

    @Test
    public void encodeUserinfo_noMatchingAlgorithm_noClientCertificate() throws Exception {
        this.testEncodeUserinfo("unknown", null, "token_default");
    }

    @Test
    public void encodeUserinfo_noMatchingAlgorithm_noClientCertificateFound() throws Exception {
        this.testEncodeUserinfo("unknown", "notExistingId", "token_default");
    }

    @Test
    public void encodeUserinfo_noMatchingAlgorithm_clientCertificateFound() throws Exception {
        this.testEncodeUserinfo("unknown", "existingId", "token_rs_256");
    }

    @Test
    public void encodeUserinfo_matchingAlgorithm() throws Exception {
        this.testEncodeUserinfo("RS512", null, "token_rs_512");
    }

    private void testEncodeUserinfo(String algorithm, String clientCertificate, String expectedResult) throws Exception {
        Client client = new Client();
        client.setUserinfoSignedResponseAlg(algorithm);
        client.setCertificate(clientCertificate);

        var test = jwtService.encodeUserinfo(new JWT(), client).test();
        test.await(10, TimeUnit.SECONDS);
        test.assertComplete()
                .assertValue(o -> o.equals(expectedResult));
    }

    @Test
    public void jwt_should_be_decoded_with_base_64_url () {
        final String jwtWithUnderscore = "eyJraWQiOiJkZWZhdWx0IiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYifQ.eyJnaXMiOiJkZWZhdWx0LWlkcC0xYzZmMjY3Zi1kOGJhLTQwYTAtYWYyNi03ZmQ4YmE3MGEwYjE6NzhjYmU2YzUtNThlNC00MDUyLThiZTYtYzU1OGU0MzA1MmRiIiwic3ViIjoiMzE2NjdkOWMtMjYxYi0zODQ0LTk3NDAtYzFiMWZkODY4ZTVkIiwiYXVkIjoidGVzdCIsImRpc3BsYXlOYW1lIjoiQm_Dq2dlbiBNYWxlciIsImRvbWFpbiI6IjFjNmYyNjdmLWQ4YmEtNDBhMC1hZjI2LTdmZDhiYTcwYTBiMSIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA5Mi90ZXN0L29pZGMiLCJleHAiOjE3NDMwODQ0NjUsImlhdCI6MTc0MzA3NzI2NSwianRpIjoiZDg5MU5LSkdiTGhTb3ZhT0tSM1ZYQndUNzZQRmMtUmRYcmFpa3Fpa2xiWSJ9.fZzgodapXlCQPaqB2kQ-C_1aQAwZJdQL1g5j8gAfH2TTQABhUwWBpaRzag9-rytONnE97d631g5qlZfgF2bkBx8kaTpiqKJlHxG2-4LREWDs5iVao4AtGb1JoUR4G50p_vRqqviRO9Vby0E6l8XHE3faxpF-k5_BPcNKwpzJdmkkvAYInAXLAy2Av9vHCALm7FYwhmUtvy2TaRSMwu0umL6RtnGyueVsGKL5HxpvYRe02RMa6vAEsmbJMtZ602O1ThQmcbARUvPf564YslUXADxat5SOp7AqLHfYqUPNaeZjvlGVZDFndmkFTHyVbuf-syNmu69TNMRWw6jd7EREkQ";
        jwtService.decode(jwtWithUnderscore, JWTService.TokenType.ACCESS_TOKEN)
                .test()
                .assertValue(jwt -> jwt.get("displayName").equals("Boëgen Maler"));
    }

    @Test
    public void encode_noClientCertificateFound_noFallback() throws Exception {
        jwtService = new JWTServiceImpl(certificateManager, new ObjectMapper(), false);

        Client client = new Client();
        client.setCertificate("notExistingId");

        TestObserver<String> testObserver = jwtService.encode(new JWT(), client).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertError(TemporarilyUnavailableException.class);
    }

}
