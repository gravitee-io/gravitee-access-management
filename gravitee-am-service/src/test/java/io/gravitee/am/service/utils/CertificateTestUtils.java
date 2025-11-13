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
package io.gravitee.am.service.utils;

import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import io.gravitee.am.certificate.api.X509CertUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Utility class for generating test certificates.
 *
 * @author GraviteeSource Team
 */
public final class CertificateTestUtils {

    private CertificateTestUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Generate a valid certificate PEM for testing.
     * Certificate is valid for 10 years from now.
     *
     * @return PEM-encoded certificate string
     */
    public static String generateValidCertificatePEM() {
        return generateCertificatePEM("CN=Test Certificate, O=Test Org, C=US", -1, 10);
    }

    /**
     * Generate an expired certificate PEM for testing.
     * Certificate is expired 1 year ago (valid for 1 year, expired 1 year ago).
     *
     * @return PEM-encoded certificate string
     */
    public static String generateExpiredCertificatePEM() {
        return generateCertificatePEM("CN=Expired Test Certificate, O=Test Org, C=US", -2, -1);
    }

    /**
     * Generate a certificate PEM with custom validity period.
     *
     * @param subjectDN the subject distinguished name (e.g., "CN=Test Certificate, O=Test Org, C=US")
     * @param yearsFromStart number of years from now for certificate start date (negative = past)
     * @param yearsFromStart number of years from now for certificate end date (negative = past)
     * @return PEM-encoded certificate string
     */
    private static String generateCertificatePEM(String subjectDN, int yearsFromStart, int yearsFromEnd) {
        try {
            // Generate key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            // Create certificate with specified dates
            X500Name dnName = new X500Name(subjectDN);
            Date now = new Date();
            Date from = new Date(now.getTime() + (yearsFromStart * 365L * 24 * 60 * 60 * 1000));
            Date to = new Date(now.getTime() + (yearsFromEnd * 365L * 24 * 60 * 60 * 1000));

            BigInteger certSerialNumber = new BigInteger(Long.toString(from.getTime()));

            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA")
                    .setProvider(BouncyCastleProviderSingleton.getInstance())
                    .build(keyPair.getPrivate());

            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    dnName, certSerialNumber, from, to, dnName, keyPair.getPublic());

            // Add basic constraints
            BasicConstraints basicConstraints = new BasicConstraints(false);
            certBuilder.addExtension(new ASN1ObjectIdentifier("2.5.29.19"), true, basicConstraints);

            X509Certificate cert = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProviderSingleton.getInstance())
                    .getCertificate(certBuilder.build(contentSigner));

            return X509CertUtils.toPEMString(cert);
        } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
            throw new RuntimeException("Failed to generate certificate for testing", e);
        }
    }
}

