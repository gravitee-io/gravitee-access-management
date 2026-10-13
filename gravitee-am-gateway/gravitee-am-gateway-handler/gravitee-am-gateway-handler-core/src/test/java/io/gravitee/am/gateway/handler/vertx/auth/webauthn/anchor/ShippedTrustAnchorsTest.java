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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;

import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.ANDROID_KEY;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.ANDROID_SAFETYNET;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.APPLE;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.MDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The trust anchors shipped with the distribution: the roots vertx-auth-webauthn and AM embedded in their
 * code before AM-5812, the Android Keystore root that expired on 2026-05-24 left out.
 *
 * @author GraviteeSource Team
 */
class ShippedTrustAnchorsTest {

    private final WebAuthnTrustAnchorRegistry registry = ShippedTrustAnchors.registry();

    @Test
    void ship_the_android_key_roots() {
        assertThat(fingerprints(registry.roots(ANDROID_KEY))).containsExactly(
                // Android Keystore Software Attestation Root, expires 2036-01-06
                "51d496ad4664190fbdaf1ad987278efa35d6fbf52e50a9c34ded8395477ede5c",
                // Google hardware attestation root (serialNumber=f92009e853b6b045) issued 2019, expires 2034-11-18
                "1ef1a04b8ba58ab94589ac498c8982a783f24ea7307e0159a0c3a73b377d87cc",
                // Google hardware attestation root issued 2021, expires 2036-11-13
                "ab6641178a36e179aa0c1cdddf9a16eb45fa20943e2b8cd7c7c05c26cf8b487a",
                // Google hardware attestation root issued 2022, expires 2042-03-15
                "cedb1cb6dc896ae5ec797348bce9286753c2b38ee71ce0fbe34a9a1248800dfc",
                // Key Attestation CA1, Google LLC, expires 2035-07-15
                "6d9db4ce6c5c0b293166d08986e05774a8776ceb525d9e4329520de12ba4bcc0");
    }

    @Test
    void ship_one_root_for_apple_android_safetynet_and_mds() {
        // Apple WebAuthn Root CA, expires 2045-03-15
        assertThat(fingerprints(registry.roots(APPLE))).containsExactly("0915dd5c07a28db549d1f677bb5a75d4bfbe9561a773424327762e9e02f9bb29");
        // GlobalSign Root CA (R1), expires 2028-01-28
        assertThat(fingerprints(registry.roots(ANDROID_SAFETYNET))).containsExactly("ebd41040e4bb3ec742c9e381d31ef2a41a48b6685c96e7cef3c1df6cd4331c99");
        // GlobalSign Root CA - R3, signer of the FIDO MDS3 BLOB, expires 2029-03-18
        assertThat(fingerprints(registry.roots(MDS))).containsExactly("cbb522d7b7f127ad6a0113865bdf1cd4102e7d0759af635a7cf4720dc963c53b");
    }

    @Test
    void ship_only_self_signed_roots_named_after_a_format() {
        assertThat(registry.sharedRoots()).isEmpty();
        for (String scope : WebAuthnTrustAnchorRegistry.SCOPES) {
            for (X509Certificate root : registry.roots(scope)) {
                assertThat(root.getIssuerX500Principal()).as("%s is self-signed", root.getSubjectX500Principal()).isEqualTo(root.getSubjectX500Principal());
            }
        }
    }

    private static List<String> fingerprints(List<X509Certificate> certificates) {
        return certificates.stream().map(ShippedTrustAnchorsTest::sha256).toList();
    }

    private static String sha256(X509Certificate certificate) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
