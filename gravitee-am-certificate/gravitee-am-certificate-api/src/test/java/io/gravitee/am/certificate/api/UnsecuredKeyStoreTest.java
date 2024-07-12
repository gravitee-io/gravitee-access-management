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

import io.gravitee.am.model.Certificate;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Base64;
import java.util.Map;

public class UnsecuredKeyStoreTest {

    @Test
    public void should_load_keystore_from_certificate(){
        // given
        String base64 = "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";

        Certificate certificate = new Certificate();
        certificate.setConfiguration("{\"alias\": \"1\", \"keypass\": \"password\"}");
        certificate.setMetadata(Map.of(CertificateMetadata.FILE, Base64.getDecoder().decode(base64)));

        // when
        UnsecuredKeyStore ks = UnsecuredKeyStore.load(certificate);

        // then
        Assertions.assertEquals("1", ks.getAlias());
        Assertions.assertEquals("password", new String(ks.getPassword()));
        Assertions.assertNotNull(ks.getKeyStore());
    }

    @Test
    public void should_return_empty_when_default_trust_store_is_not_loaded(){
        Assertions.assertTrue(UnsecuredKeyStore.loadDefaultTrustStore().isEmpty());
    }

}