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

import io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.ShippedTrustAnchors;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry;
import org.junit.jupiter.api.Test;

import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.ANDROID_KEY;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.ANDROID_SAFETYNET;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.APPLE;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.MDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class GraviteeWebAuthnOptionsTest {

    @Test
    void takes_its_roots_from_the_trust_anchors() {
        WebAuthnTrustAnchorRegistry trustAnchors = ShippedTrustAnchors.registry();

        GraviteeWebAuthnOptions options = new GraviteeWebAuthnOptions(trustAnchors);

        assertThat(options.getRootCertificates()).containsOnlyKeys(ANDROID_KEY, ANDROID_SAFETYNET, APPLE, MDS);
        assertThat(options.getRootCertificate(APPLE)).isEqualTo(trustAnchors.roots(APPLE).get(0));
        assertThat(options.getRootCertificate(ANDROID_SAFETYNET)).isEqualTo(trustAnchors.roots(ANDROID_SAFETYNET).get(0));
        assertThat(options.getRootCertificate(MDS)).isEqualTo(trustAnchors.roots(MDS).get(0));
        assertThat(options.getRootCertificate(ANDROID_KEY)).isEqualTo(trustAnchors.roots(ANDROID_KEY).get(0));
        assertThat(options.getAdditionalRootCertificate(ANDROID_KEY)).containsExactlyElementsOf(trustAnchors.roots(ANDROID_KEY));
    }

    @Test
    void drops_the_roots_embedded_in_vertx_auth_webauthn() {
        GraviteeWebAuthnOptions options = new GraviteeWebAuthnOptions(WebAuthnTrustAnchorRegistry.empty());

        assertThat(options.getRootCertificates()).isNullOrEmpty();
        assertThat(options.getAdditionalRootCertificate(ANDROID_KEY)).isNull();
    }

    @Test
    void lets_a_domain_certificate_replace_the_root_of_a_format() {
        WebAuthnTrustAnchorRegistry trustAnchors = ShippedTrustAnchors.registry();
        GraviteeWebAuthnOptions options = new GraviteeWebAuthnOptions(trustAnchors);
        String domainRoot = java.util.Base64.getEncoder().encodeToString(encoded(trustAnchors.roots(ANDROID_SAFETYNET).get(0)));

        options.putRootCertificate(APPLE, domainRoot);

        assertThat(options.getRootCertificate(APPLE)).isEqualTo(trustAnchors.roots(ANDROID_SAFETYNET).get(0));
    }

    private static byte[] encoded(java.security.cert.X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (java.security.cert.CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
