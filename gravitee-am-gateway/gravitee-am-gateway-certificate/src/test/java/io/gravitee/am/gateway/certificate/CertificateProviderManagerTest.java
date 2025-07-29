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

import com.nimbusds.jose.JOSEException;
import io.gravitee.am.certificate.api.CertificateMetadata;
import io.gravitee.am.certificate.api.DefaultKey;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.certificate.impl.CertificateProviderManagerImpl;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.model.jose.JWK;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private final CertificateProviderManager certificateProviderManager = new CertificateProviderManagerImpl();

    @Test
    public void noneAlgorithmCertificateProvider_nominalCase() {
        CertificateProvider certificateProvider = certificateProviderManager.create(noneProvider());

        JWT jwt = new JWT();
        jwt.setSub("sub");
        jwt.setIss("iss");

        assertEquals("eyJhbGciOiJub25lIn0.eyJpc3MiOiJpc3MiLCJzdWIiOiJzdWIifQ.", certificateProvider.getJwtBuilder().sign(jwt));
    }

    @Test
    public void noneAlgorithmCertificateProvider_accessToProviderProperty() throws Exception {
        CertificateProvider certificateProvider = certificateProviderManager.create(noneProvider());
        assertEquals("none", certificateProvider.getProvider().signatureAlgorithm());
        assertEquals("none", certificateProvider.getProvider().certificateMetadata().getMetadata().get("digestAlgorithmName"));
        assertTrue(certificateProvider.getProvider().jwtBuilder().isEmpty());
        assertTrue(certificateProvider.getProvider().jwtParser().isEmpty());
    }

    @Test
    public void jwtProcessorCertificateProvider() throws Exception {
        io.gravitee.am.certificate.api.CertificateProvider mockProvider = jwtProcessorProvider();

        final var certProvider = certificateProviderManager.create(mockProvider);
        assertEquals(mockProvider, certProvider.getProvider());

        assertEquals(mockProvider.jwtBuilder().get(), certProvider.getJwtBuilder());
        assertEquals(mockProvider.jwtParser().get(), certProvider.getJwtParser());
    }

    @Test
    public void defaultCertificateProvider_nominalCase() {
        CertificateProvider certificateProvider = certificateProviderManager.create(defaultProvider());

        JWT jwt = new JWT();
        jwt.setIss("iss");
        jwt.setSub("sub");

        assertEquals(
                "eyJraWQiOiJkZWZhdWx0LWdyYXZpdGVlLUFNLWtleSIsInR5cCI6IkpXVCIsImFsZyI6IkhTMjU2In0.eyJpc3MiOiJpc3MiLCJzdWIiOiJzdWIifQ.BrJoRE3QH-4oNB6Off46x6-vLgS1Dk6Fi_IRmSmRMhA",
                certificateProvider.getJwtBuilder().sign(jwt)
        );
    }

    private io.gravitee.am.certificate.api.CertificateProvider noneProvider() {
        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, "none"));

        return new io.gravitee.am.certificate.api.CertificateProvider() {
            @Override
            public Optional<Date> getExpirationDate() {
                return Optional.empty();
            }

            @Override
            public Flowable<JWK> privateKey() {
                throw new UnsupportedOperationException("No private key for \"none\" algorithm");
            }

            @Override
            public Single<io.gravitee.am.certificate.api.Key> key() {
                throw new UnsupportedOperationException("No key for \"none\" algorithm");
            }

            @Override
            public Single<String> publicKey() {
                throw new UnsupportedOperationException("No public key for \"none\" algorithm");
            }

            @Override
            public Flowable<JWK> keys() {
                throw new UnsupportedOperationException("No keys for \"none\" algorithm");
            }

            @Override
            public String signatureAlgorithm() {
                return "none";
            }

            @Override
            public CertificateMetadata certificateMetadata() {
                return certificateMetadata;
            }
        };
    }

    private io.gravitee.am.certificate.api.CertificateProvider defaultProvider() {
        // create default signing HMAC key
        Key key = new SecretKeySpec(signingKeySecret.getBytes(), "HmacSHA256");
        io.gravitee.am.certificate.api.Key certificateKey = new DefaultKey(signingKeyId, key);

        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, defaultDigestAlgorithm));

        return new io.gravitee.am.certificate.api.CertificateProvider() {
            @Override
            public Optional<Date> getExpirationDate() {
                return Optional.empty();
            }

            @Override
            public Flowable<JWK> privateKey() {
               return null;
            }

            @Override
            public Single<io.gravitee.am.certificate.api.Key> key() {
                return Single.just(certificateKey);
            }

            @Override
            public Single<String> publicKey() {
                return null;
            }

            @Override
            public Flowable<JWK> keys() {
                return null;
            }

            @Override
            public String signatureAlgorithm() {
                return "HS256";
            }

            @Override
            public CertificateMetadata certificateMetadata() {
                return certificateMetadata;
            }
        };
    }


    private io.gravitee.am.certificate.api.CertificateProvider jwtProcessorProvider() {
        // create default signing HMAC key
        Key key = new SecretKeySpec(signingKeySecret.getBytes(), "HmacSHA256");
        io.gravitee.am.certificate.api.Key certificateKey = new DefaultKey(signingKeyId, key);

        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, defaultDigestAlgorithm));

        return new io.gravitee.am.certificate.api.CertificateProvider() {
            private final JWTBuilder jwtBuilder = Mockito.mock(JWTBuilder.class);
            private final JWTParser jwtParser = Mockito.mock(JWTParser.class);
            @Override
            public Optional<Date> getExpirationDate() {
                return Optional.empty();
            }

            @Override
            public Flowable<JWK> privateKey() {
                return null;
            }

            @Override
            public Single<io.gravitee.am.certificate.api.Key> key() {
                return Single.just(certificateKey);
            }

            @Override
            public Single<String> publicKey() {
                return null;
            }

            @Override
            public Flowable<JWK> keys() {
                return null;
            }

            @Override
            public String signatureAlgorithm() {
                return "HS256";
            }

            @Override
            public CertificateMetadata certificateMetadata() {
                return certificateMetadata;
            }

            @Override
            public Optional<JWTBuilder> jwtBuilder() throws InvalidKeyException, JOSEException {
                return Optional.of(jwtBuilder);
            }

            @Override
            public Optional<JWTParser> jwtParser() throws InvalidKeyException, JOSEException  {
                return Optional.of(jwtParser);
            }

        };
    }
}
