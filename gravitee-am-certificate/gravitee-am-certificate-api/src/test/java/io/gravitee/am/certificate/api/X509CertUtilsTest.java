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

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

/**
 * @author GraviteeSource Team
 */
public class X509CertUtilsTest {

    // Test certificate PEM (valid self-signed certificate)
    private static final String TEST_CERTIFICATE_PEM = """
            -----BEGIN CERTIFICATE-----
            MIICCTCCAa+gAwIBAgIUN7ooxea0kJHv18V9kpQ7Xen2gSowCgYIKoZIzj0EAwIw
            WjELMAkGA1UEBhMCRlIxDTALBgNVBAgMBE5vcmQxDjAMBgNVBAcMBUxpbGxlMRcw
            FQYDVQQKDA5NeU9yZ2FuaXphdGlvbjETMBEGA1UEAwwKQ29tbW9uTmFtZTAeFw0y
            NTA1MjIxNzU4NTRaFw0yNTA2MjExNzU4NTRaMFoxCzAJBgNVBAYTAkZSMQ0wCwYD
            VQQIDAROb3JkMQ4wDAYDVQQHDAVMaWxsZTEXMBUGA1UECgwOTXlPcmdhbml6YXRp
            b24xEzARBgNVBAMMCkNvbW1vbk5hbWUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNC
            AARRtfM1dgex0RW2Zf+vbWX1NCKxxVVmreKn3zMGuDGjFlqWc0VKe2wQal032H3H
            qaH2ju/wPHhihhIPE1i7m7alo1MwUTAdBgNVHQ4EFgQUUkMQCUtFHNyGoZ0Qv+gf
            mpsH/iEwHwYDVR0jBBgwFoAUUkMQCUtFHNyGoZ0Qv+gfmpsH/iEwDwYDVR0TAQH/
            BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiByzweeBYRCDSesw/3++jcesRZddaxE
            yhFkQJuzSYRwiQIhAK9WZoFE8dCVi8403a8e5jql6PKwkVjVt4ZX/bWAeq5U
            -----END CERTIFICATE-----
            """;

    @Test
    public void shouldCalculateThumbprintWithSHA256() throws Exception {
        X509Certificate cert = X509CertUtils.parse(TEST_CERTIFICATE_PEM);
        Assertions.assertNotNull(cert, "Certificate should be parsed successfully");

        String thumbprint = X509CertUtils.getThumbprint(cert, "SHA-256");

        Assertions.assertNotNull(thumbprint, "Thumbprint should not be null");
        Assertions.assertFalse(thumbprint.isEmpty(), "Thumbprint should not be empty");
        // Base64URL encoding should not contain padding (=) or standard Base64 characters (+/)
        Assertions.assertFalse(thumbprint.contains("="), "Base64URL should not contain padding");
        Assertions.assertFalse(thumbprint.contains("+"), "Base64URL should not contain +");
        Assertions.assertFalse(thumbprint.contains("/"), "Base64URL should not contain /");
    }

    @Test
    public void shouldCalculateThumbprintWithSHA1() throws Exception {
        X509Certificate cert = X509CertUtils.parse(TEST_CERTIFICATE_PEM);
        Assertions.assertNotNull(cert, "Certificate should be parsed successfully");

        String thumbprint = X509CertUtils.getThumbprint(cert, "SHA-1");

        Assertions.assertNotNull(thumbprint, "Thumbprint should not be null");
        Assertions.assertFalse(thumbprint.isEmpty(), "Thumbprint should not be empty");
        // SHA-1 produces shorter digest, so thumbprint should be shorter than SHA-256
        String sha256Thumbprint = X509CertUtils.getThumbprint(cert, "SHA-256");
        Assertions.assertTrue(thumbprint.length() < sha256Thumbprint.length(),
                "SHA-1 thumbprint should be shorter than SHA-256 thumbprint");
    }

    @Test
    public void shouldProduceConsistentThumbprint() throws Exception {
        X509Certificate cert = X509CertUtils.parse(TEST_CERTIFICATE_PEM);
        Assertions.assertNotNull(cert, "Certificate should be parsed successfully");

        String thumbprint1 = X509CertUtils.getThumbprint(cert, "SHA-256");
        String thumbprint2 = X509CertUtils.getThumbprint(cert, "SHA-256");

        Assertions.assertEquals(thumbprint1, thumbprint2,
                "Same certificate should produce the same thumbprint");
    }

    @Test
    public void shouldProduceDifferentThumbprintsForDifferentAlgorithms() throws Exception {
        X509Certificate cert = X509CertUtils.parse(TEST_CERTIFICATE_PEM);
        Assertions.assertNotNull(cert, "Certificate should be parsed successfully");

        String sha256Thumbprint = X509CertUtils.getThumbprint(cert, "SHA-256");
        String sha1Thumbprint = X509CertUtils.getThumbprint(cert, "SHA-1");

        Assertions.assertNotEquals(sha256Thumbprint, sha1Thumbprint,
                "Different algorithms should produce different thumbprints");
    }

    @Test
    public void shouldProduceSameThumbprintForEqualCertificates() throws Exception {
        X509Certificate cert1 = X509CertUtils.parse(TEST_CERTIFICATE_PEM);
        Assertions.assertNotNull(cert1, "Certificate 1 should be parsed successfully");

        // Create a different certificate by parsing the same PEM (should be identical)
        // For a truly different certificate, we'd need a different PEM, but for this test
        // we'll verify that the same certificate produces the same thumbprint
        X509Certificate cert2 = X509CertUtils.parse(TEST_CERTIFICATE_PEM);
        Assertions.assertNotNull(cert2, "Certificate 2 should be parsed successfully");

        String thumbprint1 = X509CertUtils.getThumbprint(cert1, "SHA-256");
        String thumbprint2 = X509CertUtils.getThumbprint(cert2, "SHA-256");

        // Same certificate should produce same thumbprint
        Assertions.assertEquals(thumbprint1, thumbprint2,
                "Same certificate should produce the same thumbprint");
    }

    @Test
    public void shouldThrowExceptionForInvalidAlgorithm() {
        X509Certificate cert = X509CertUtils.parse(TEST_CERTIFICATE_PEM);
        Assertions.assertNotNull(cert, "Certificate should be parsed successfully");

        Assertions.assertThrows(NoSuchAlgorithmException.class, () -> {
            X509CertUtils.getThumbprint(cert, "INVALID-ALGORITHM");
        }, "Should throw NoSuchAlgorithmException for invalid algorithm");
    }

    @Test
    public void shouldProduceBase64URLEncodedThumbprint() throws Exception {
        X509Certificate cert = X509CertUtils.parse(TEST_CERTIFICATE_PEM);
        Assertions.assertNotNull(cert, "Certificate should be parsed successfully");

        String thumbprint = X509CertUtils.getThumbprint(cert, "SHA-256");

        // Base64URL encoding uses - and _ instead of + and /
        // Should only contain alphanumeric characters, -, and _
        Assertions.assertTrue(thumbprint.matches("^[A-Za-z0-9_-]+$"),
                "Thumbprint should be Base64URL encoded (only alphanumeric, -, _)");
    }

    @Test
    public void shouldHandleNullCertificate() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            X509CertUtils.getThumbprint(null, "SHA-256");
        }, "Should throw NullPointerException for null certificate");
    }
}

