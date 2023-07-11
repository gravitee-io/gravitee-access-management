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
package io.gravitee.am.gateway.certificate;

import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.DefaultKey;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.certificate.dummy.DefaultProvider;
import io.gravitee.am.gateway.certificate.dummy.KeyPairProvider;
import io.gravitee.am.gateway.certificate.dummy.NoneProvider;
import io.gravitee.am.gateway.certificate.impl.CertificateProviderManagerImpl;

import java.security.*;
import java.security.spec.*;
import java.util.stream.Stream;
import javax.crypto.spec.SecretKeySpec;
import java.util.Collections;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateProviderManagerTest {

    private static final String signingKeySecret = "s3cR3t4grAv1t3310AMS1g1ingDftK3y";
    private static final String signingKeyId = "default-gravitee-AM-key";
    private static final String defaultDigestAlgorithm = "SHA-256";

    private static final CertificateProviderManager certificateProviderManager = new CertificateProviderManagerImpl();
    private static final String NONE_SIGNED_JWT = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJzdWIiLCJpc3MiOiJpc3MifQ.";
    private static final String DEFAULT_SIGNED_JWT = "eyJraWQiOiJkZWZhdWx0LWdyYXZpdGVlLUFNLWtleSIsInR5cCI6IkpXVCIsImFsZyI6IkhTMjU2In0.eyJzdWIiOiJzdWIiLCJpc3MiOiJpc3MifQ.Ti366cJSMVSnvFW1wHYFMdc63zTdIpa42O6AOTWyGKk";
    private static final String KEYPAIR_SIGNED_JWT = "eyJraWQiOiJkZWZhdWx0LWdyYXZpdGVlLUFNLWtleSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.";


    @ParameterizedTest(name = "Must sign jwt with [{1}]")
    @MethodSource("params_that_must_sign_jwt")
    public void must_sign_jwt(JWT jwt, io.gravitee.am.certificate.api.CertificateProvider certificateProvider, String expected) {
        CertificateProvider provider = certificateProviderManager.create(certificateProvider);

        final String signed = provider.getJwtBuilder().sign(jwt);
        assertTrue(signed.contains(expected));
    }

    private static Stream<Arguments> params_that_must_sign_jwt() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, InvalidKeySpecException {
        JWT jwt = new JWT();
        jwt.setIss("iss");
        jwt.setSub("sub");
        return Stream.of(
                Arguments.of(jwt, noneProvider(), NONE_SIGNED_JWT),
                Arguments.of(jwt, defaultProvider(), DEFAULT_SIGNED_JWT),
                Arguments.of(jwt, keyPairProvider(), KEYPAIR_SIGNED_JWT)
        );
    }

    @ParameterizedTest(name = "Must access provider properties of [{1}]")
    @MethodSource("params_that_must_access_to_provider_properties")
    public void must_access_to_provider_properties(io.gravitee.am.certificate.api.CertificateProvider certificateProvider, String signature, String digestAlg) {
        CertificateProvider provider = certificateProviderManager.create(certificateProvider);

        assertEquals(signature, provider.getProvider().signatureAlgorithm());
        assertEquals(digestAlg, provider.getProvider().certificateMetadata().getMetadata().get("digestAlgorithmName"));
    }

    private static Stream<Arguments> params_that_must_access_to_provider_properties() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, InvalidKeySpecException {
        return Stream.of(
                Arguments.of(noneProvider(), "none", "none"),
                Arguments.of(defaultProvider(), "HS256", "SHA-256"),
                Arguments.of(keyPairProvider(), "ES256", "SunEC")
        );
    }

    private static io.gravitee.am.certificate.api.CertificateProvider noneProvider() {
        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, "none"));
        return new NoneProvider(certificateMetadata);
    }

    private static io.gravitee.am.certificate.api.CertificateProvider defaultProvider() {
        // create default signing HMAC key
        Key key = new SecretKeySpec(signingKeySecret.getBytes(), "HmacSHA256");
        io.gravitee.am.certificate.api.Key certificateKey = new DefaultKey(signingKeyId, key);

        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, defaultDigestAlgorithm));

        return new DefaultProvider(certificateMetadata, certificateKey);
    }

    private static io.gravitee.am.certificate.api.CertificateProvider keyPairProvider() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeySpecException {
        // create default signing HMAC key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));

        io.gravitee.am.certificate.api.Key certificateKey = new DefaultKey(signingKeyId, kpg.generateKeyPair());

        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, kpg.getProvider().getName()));

        return new KeyPairProvider(certificateMetadata, certificateKey);
    }
}
