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
package io.gravitee.am.certificate.api;

import io.gravitee.am.model.jose.JWK;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class CertificateProviderTest {

    @Test
    public void shouldUseBlockingSignerForLegacyHsmProvider() {
        assertTrue(new AwsHsmCertificateProvider().useBlockingSigner());
    }

    @Test
    public void shouldNotUseBlockingSignerForPlainProvider() {
        assertFalse(new PlainCertificateProvider().useBlockingSigner());
    }

    private static class AwsHsmCertificateProvider extends StubCertificateProvider {
    }

    private static class PlainCertificateProvider extends StubCertificateProvider {
    }

    private static class StubCertificateProvider implements CertificateProvider {

        @Override
        public Optional<Date> getExpirationDate() {
            return Optional.empty();
        }

        @Override
        public Single<Key> key() {
            return Single.never();
        }

        @Override
        public Single<String> publicKey() {
            return Single.never();
        }

        @Override
        public Flowable<JWK> privateKey() {
            return Flowable.empty();
        }

        @Override
        public Flowable<JWK> keys() {
            return Flowable.empty();
        }

        @Override
        public CertificateMetadata certificateMetadata() {
            return null;
        }

        @Override
        public String signatureAlgorithm() {
            return null;
        }
    }
}
