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
import io.gravitee.am.gateway.certificate.impl.CertificateProviderManagerImpl;
import io.gravitee.am.model.jose.JWK;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateProviderManagerTest {

    private static final String signingKeySecret = "s3cR3t4grAv1t3310AMS1g1ingDftK3y";
    private static final String signingKeyId = "default-gravitee-AM-key";
    private static final String defaultDigestAlgorithm = "SHA-256";

    private CertificateProviderManager certificateProviderManager = new CertificateProviderManagerImpl();

    @Test
    public void noneAlgorithmCertificateProvider_nominalCase() {
        CertificateProvider certificateProvider = certificateProviderManager.create(noneProvider());
        JWT jwt = new JWT();
        jwt.setIss("iss");
        jwt.setSub("sub");

        assertEquals(
                "non matching jwt with none algorithm",
                "eyJhbGciOiJub25lIn0.eyJzdWIiOiJzdWIiLCJpc3MiOiJpc3MifQ.", certificateProvider.getJwtBuilder().sign(jwt)
        );
    }

    @Test
    public void noneAlgorithmCertificateProvider_accessToProviderProperty() {
        CertificateProvider certificateProvider = certificateProviderManager.create(noneProvider());
        assertEquals("none", certificateProvider.getProvider().signatureAlgorithm());
        assertEquals("none", certificateProvider.getProvider().certificateMetadata().getMetadata().get("digestAlgorithmName"));
    }

    @Test
    public void defaultCertificateProvider_nominalCase() {
        CertificateProvider certificateProvider = certificateProviderManager.create(defaultProvider());

        JWT jwt = new JWT();
        jwt.setIss("iss");
        jwt.setSub("sub");

        assertEquals(
                "non matching jwt with default certificateProvider",
                "eyJraWQiOiJkZWZhdWx0LWdyYXZpdGVlLUFNLWtleSIsInR5cCI6IkpXVCIsImFsZyI6IkhTMjU2In0.eyJzdWIiOiJzdWIiLCJpc3MiOiJpc3MifQ.Ti366cJSMVSnvFW1wHYFMdc63zTdIpa42O6AOTWyGKk",
                certificateProvider.getJwtBuilder().sign(jwt)
        );
    }

    private io.gravitee.am.certificate.api.CertificateProvider noneProvider() {
        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, "none"));

        io.gravitee.am.certificate.api.CertificateProvider noneProvider = new io.gravitee.am.certificate.api.CertificateProvider() {
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
        return noneProvider;
    }

    private io.gravitee.am.certificate.api.CertificateProvider defaultProvider() {
        // create default signing HMAC key
        Key key = new SecretKeySpec(signingKeySecret.getBytes(), "HmacSHA256");
        io.gravitee.am.certificate.api.Key certificateKey = new DefaultKey(signingKeyId, key);

        CertificateMetadata certificateMetadata = new CertificateMetadata();
        certificateMetadata.setMetadata(Collections.singletonMap(CertificateMetadata.DIGEST_ALGORITHM_NAME, defaultDigestAlgorithm));

        io.gravitee.am.certificate.api.CertificateProvider defaultProvider = new io.gravitee.am.certificate.api.CertificateProvider() {
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
        return defaultProvider;
    }
}
