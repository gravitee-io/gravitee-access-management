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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn;

import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the attestation root certificates compiled into {@link GraviteeWebAuthnOptions}.
 *
 * <p>These certificates expire. When {@code ANDROID_KEYSTORE_ROOT_1} lapsed on 24 May 2026 the
 * gateway stopped booting for six customers in six days: the {@code webAuthn} bean threw during
 * context initialisation, {@code rootProvider} failed to build, and no security domain deployed at
 * all — including on installations that had never enabled WebAuthn.
 *
 * <p>AM-7055 fixed the outage by making that non-fatal. {@code pushAdditionalRootCertificate} now
 * catches the exception from {@code checkValidity()}, logs a warning, and omits the certificate.
 * The gateway starts, which is the right outcome, but the trust set has quietly shrunk and nothing
 * reports it.
 *
 * <p>What matters is therefore not how many certificates load — several roots are rotated issues of
 * the same identity, and a superseded one lapsing is expected and harmless. What matters is that no
 * <em>identity</em> loses all of its valid certificates. These tests assert that, and warn while
 * there is still time to ship a replacement.
 */
class GraviteeWebAuthnOptionsTest {

    /** The key every embedded Android root is pushed under by {@code initAdditionalRootCertificates()}. */
    private static final String ANDROID_KEY = "android-key";

    /** Long enough to cut, review and release a patch across the supported branches. */
    private static final Duration WARNING_WINDOW = Duration.ofDays(90);

    /**
     * The distinct identities anchored by the embedded roots:
     * <ol>
     *   <li>{@code CN=Android Keystore Software Attestation Root} — software attestation</li>
     *   <li>{@code serialNumber=f92009e853b6b045} — hardware attestation, carried by four rotated issues</li>
     *   <li>{@code CN=Key Attestation CA1} — the current key attestation CA</li>
     * </ol>
     * Counting identities rather than certificates is deliberate: a superseded issue expiring is
     * expected and harmless, an identity losing its last valid certificate is not.
     */
    private static final int EXPECTED_ROOT_IDENTITY_COUNT = 3;

    @Test
    void shouldKeepAValidCertificateForEveryAttestationRootIdentity() {
        List<String> identities = subjectsOf(androidRootCertificates());

        assertThat(identities)
                .as("An attestation root identity has no valid certificate left, so chains anchored to it "
                        + "can no longer be validated — and nothing fails at start-up to say so, because an "
                        + "expired or unparseable certificate is dropped silently. Check the gateway log for "
                        + "'Invalid additional root certificate' and add a replacement to "
                        + "GraviteeWebAuthnOptions. Surviving identities: %s", identities)
                .hasSize(EXPECTED_ROOT_IDENTITY_COUNT);
    }

    @Test
    void shouldNotHaveAnAttestationRootIdentityExpiringSoon() {
        Instant deadline = Instant.now().plus(WARNING_WINDOW);

        // Group by identity: a rotated-out certificate expiring is expected, provided a newer issue of
        // the same subject is still valid. Only the longest-lived certificate per subject matters.
        Map<String, Instant> lastValidPerSubject = androidRootCertificates().stream()
                .collect(Collectors.toMap(
                        certificate -> certificate.getSubjectX500Principal().getName(),
                        certificate -> certificate.getNotAfter().toInstant(),
                        (left, right) -> left.isAfter(right) ? left : right));

        List<String> expiringSoon = lastValidPerSubject.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(deadline))
                .map(entry -> entry.getKey() + " runs out " + entry.getValue())
                .toList();

        assertThat(expiringSoon)
                .as("An attestation root identity has no certificate valid beyond %d days from now. Once "
                        + "the last one lapses it is dropped without failing anything, so add a replacement "
                        + "to GraviteeWebAuthnOptions before that date.", WARNING_WINDOW.toDays())
                .isEmpty();
    }

    private List<String> subjectsOf(List<X509Certificate> certificates) {
        return certificates.stream()
                .map(certificate -> certificate.getSubjectX500Principal().getName())
                .distinct()
                .toList();
    }

    private List<X509Certificate> androidRootCertificates() {
        return new GraviteeWebAuthnOptions().getAdditionalRootCertificate(ANDROID_KEY);
    }
}
