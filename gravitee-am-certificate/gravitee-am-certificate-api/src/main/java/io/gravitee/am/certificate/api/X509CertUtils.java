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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import com.nimbusds.jose.util.Base64URL;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class X509CertUtils {

    private static final Base64.Encoder b64Enc = Base64.getEncoder();
    private static final Base64.Decoder b64Dec = Base64.getDecoder();

    /**
     * The PEM start marker.
     */
    public static final String PEM_BEGIN_MARKER = "-----BEGIN CERTIFICATE-----";

    /**
     * The PEM end marker.
     */
    public static final String PEM_END_MARKER = "-----END CERTIFICATE-----";

    /**
     * Parses a DER-encoded X.509 certificate.
     *
     * @param derEncodedCert The DER-encoded X.509 certificate, as a byte
     *                       array. May be {@code null}.
     *
     * @return The X.509 certificate, {@code null} if not specified or
     *         parsing failed.
     */
    public static X509Certificate parse(final byte[] derEncodedCert) {

        try {
            return parseWithException(derEncodedCert);
        } catch (CertificateException e) {
            return null;
        }
    }


    /**
     * Parses a DER-encoded X.509 certificate with exception handling.
     *
     * @param derEncodedCert The DER-encoded X.509 certificate, as a byte
     *                       array. Empty or {@code null} if not specified.
     *
     * @return The X.509 certificate, {@code null} if not specified.
     *
     * @throws CertificateException If parsing failed.
     */
    public static X509Certificate parseWithException(final byte[] derEncodedCert)
            throws CertificateException {

        if (derEncodedCert == null || derEncodedCert.length == 0) {
            return null;
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        final java.security.cert.Certificate cert = cf.generateCertificate(new ByteArrayInputStream(derEncodedCert));

        if (! (cert instanceof X509Certificate)) {
            throw new CertificateException("Not a X.509 certificate: " + cert.getType());
        }

        return (X509Certificate)cert;
    }


    /**
     * Parses a PEM-encoded X.509 certificate.
     *
     * @param pemEncodedCert The PEM-encoded X.509 certificate, as a
     *                       string. Empty or {@code null} if not
     *                       specified.
     *
     * @return The X.509 certificate, {@code null} if parsing failed.
     */
    public static X509Certificate parse(final String pemEncodedCert) {

        if (pemEncodedCert == null || pemEncodedCert.isEmpty()) {
            return null;
        }

        final int markerStart = pemEncodedCert.indexOf(PEM_BEGIN_MARKER);

        if (markerStart < 0) {
            return null;
        }

        String buf = pemEncodedCert.substring(markerStart + PEM_BEGIN_MARKER.length());

        final int markerEnd = buf.indexOf(PEM_END_MARKER);

        if (markerEnd < 0) {
            return null;
        }

        buf = buf.substring(0, markerEnd);

        buf = buf.replaceAll("\\s", "");

        return parse(b64Dec.decode(buf));
    }


    /**
     * Parses a PEM-encoded X.509 certificate with exception handling.
     *
     * @param pemEncodedCert The PEM-encoded X.509 certificate, as a
     *                       string. Empty or {@code null} if not
     *                       specified.
     *
     * @return The X.509 certificate, {@code null} if parsing failed.
     */
    public static X509Certificate parseWithException(final String pemEncodedCert)
            throws CertificateException {

        if (pemEncodedCert == null || pemEncodedCert.isEmpty()) {
            return null;
        }

        final int markerStart = pemEncodedCert.indexOf(PEM_BEGIN_MARKER);

        if (markerStart < 0) {
            throw new CertificateException("PEM begin marker not found");
        }

        String buf = pemEncodedCert.substring(markerStart + PEM_BEGIN_MARKER.length());

        final int markerEnd = buf.indexOf(PEM_END_MARKER);

        if (markerEnd < 0) {
            throw new CertificateException("PEM end marker not found");
        }

        buf = buf.substring(0, markerEnd);

        buf = buf.replaceAll("\\s", "");

        return parseWithException(b64Dec.decode(buf));
    }


    /**
     * Returns the specified X.509 certificate as PEM-encoded string.
     *
     * @param cert The X.509 certificate. Must not be {@code null}.
     *
     * @return The PEM-encoded X.509 certificate, {@code null} if encoding
     *         failed.
     */
    public static String toPEMString(final X509Certificate cert) {
        return toPEMString(cert, true);
    }


    /**
     * Returns the specified X.509 certificate as PEM-encoded string.
     *
     * @param cert           The X.509 certificate. Must not be
     *                       {@code null}.
     * @param withLineBreaks {@code false} to suppress line breaks.
     *
     * @return The PEM-encoded X.509 certificate, {@code null} if encoding
     *         failed.
     */
    public static String toPEMString(final X509Certificate cert, final boolean withLineBreaks) {

        StringBuilder sb = new StringBuilder();
        sb.append(PEM_BEGIN_MARKER);

        if (withLineBreaks)
            sb.append('\n');

        try {
            sb.append(b64Enc.encodeToString(cert.getEncoded()));
        } catch (CertificateEncodingException e) {
            return null;
        }

        if (withLineBreaks)
            sb.append('\n');

        sb.append(PEM_END_MARKER);
        return sb.toString();
    }

    /**
     * Calculate certificate thumbprint using the specified algorithm.
     * Returns Base64URL-encoded thumbprint (same format as used in gateway's CertificateUtils).
     *
     * @param cert the X.509 certificate. Must not be {@code null}.
     * @param algorithm the hash algorithm (e.g., "SHA-256")
     * @return Base64URL-encoded thumbprint
     * @throws NoSuchAlgorithmException if the algorithm is not available
     * @throws CertificateEncodingException if the certificate cannot be encoded
     */
    public static String getThumbprint(X509Certificate cert, String algorithm)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] der = cert.getEncoded();
        md.update(der);
        byte[] digest = md.digest();
        return Base64URL.encode(digest).toString();
    }
}
