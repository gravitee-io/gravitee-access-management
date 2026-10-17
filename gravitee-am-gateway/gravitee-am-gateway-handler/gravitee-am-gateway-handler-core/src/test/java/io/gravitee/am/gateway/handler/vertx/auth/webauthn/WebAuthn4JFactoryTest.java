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
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStorage;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.ANDROID_KEY;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.ANDROID_SAFETYNET;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.APPLE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class WebAuthn4JFactoryTest {

    private final WebAuthnTrustAnchorRegistry shipped = ShippedTrustAnchors.registry();
    private final WebAuthn4JFactory factory = new WebAuthn4JFactory();
    private final Domain domain = new Domain();
    private Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        domain.setId("domain-id");
        ReflectionTestUtils.setField(factory, "vertx", vertx);
        ReflectionTestUtils.setField(factory, "domain", domain);
        ReflectionTestUtils.setField(factory, "credentialStorage", new RepositoryCredentialStorage());
        ReflectionTestUtils.setField(factory, "trustAnchorRegistry", shipped);
    }

    @AfterEach
    void tearDown() {
        vertx.close().blockingAwait();
    }

    @Test
    void trusts_the_gateway_trust_anchors_of_each_anchored_format() {
        Map<String, List<X509Certificate>> roots = factory.trustedRoots(null);

        assertThat(roots).containsOnlyKeys(WebAuthnTrustAnchorRegistry.ANCHORED_FORMATS);
        assertThat(roots.get(ANDROID_KEY)).containsExactlyElementsOf(shipped.roots(ANDROID_KEY));
        assertThat(roots.get(APPLE)).containsExactlyElementsOf(shipped.roots(APPLE));
        assertThat(roots.get(ANDROID_SAFETYNET)).containsExactlyElementsOf(shipped.roots(ANDROID_SAFETYNET));
    }

    @Test
    void adds_the_domain_certificate_of_a_format_to_its_trust_anchors() throws Exception {
        X509Certificate domainRoot = shipped.roots(APPLE).get(0);
        WebAuthnSettings settings = new WebAuthnSettings();
        settings.setCertificates(Map.of("android-key", Base64.getEncoder().encodeToString(domainRoot.getEncoded())));

        List<X509Certificate> androidRoots = factory.trustedRoots(settings).get(ANDROID_KEY);

        assertThat(androidRoots).containsAll(shipped.roots(ANDROID_KEY)).contains(domainRoot).hasSize(shipped.roots(ANDROID_KEY).size() + 1);
    }

    @Test
    void ignores_certificates_of_formats_not_anchored_and_unreadable_certificates() throws Exception {
        WebAuthnSettings settings = new WebAuthnSettings();
        settings.setCertificates(Map.of(
                "packed", Base64.getEncoder().encodeToString(shipped.roots(APPLE).get(0).getEncoded()),
                "mds", Base64.getEncoder().encodeToString(shipped.roots(APPLE).get(0).getEncoded()),
                "apple", "not-a-certificate"));

        Map<String, List<X509Certificate>> roots = factory.trustedRoots(settings);

        assertThat(roots).containsOnlyKeys(WebAuthnTrustAnchorRegistry.ANCHORED_FORMATS);
        assertThat(roots.get(APPLE)).containsExactlyElementsOf(shipped.roots(APPLE));
    }

    @Test
    void builds_the_webauthn_bean_with_a_malformed_domain_certificate() {
        WebAuthnSettings settings = new WebAuthnSettings();
        settings.setCertificates(Map.of("android-key", "not-a-certificate"));
        domain.setWebAuthnSettings(settings);

        assertThat(factory.getObject()).isNotNull();
    }

    @Test
    void takes_the_relying_party_from_the_settings() {
        WebAuthnSettings settings = new WebAuthnSettings();
        settings.setRelyingPartyName("Custom RP name");
        settings.setOrigin("https://auth.gravitee.io:8443");
        domain.setWebAuthnSettings(settings);

        assertThat(factory.getRelyingParty().getName()).isEqualTo("Custom RP name");
        assertThat(factory.getRelyingParty().getId()).isEqualTo("auth.gravitee.io");

        settings.setRelyingPartyId("gravitee.io");

        assertThat(factory.getRelyingParty().getId()).isEqualTo("gravitee.io");
    }

    @Test
    void builds_the_webauthn_bean_without_settings() {
        assertThat(factory.getObject()).isNotNull();
        assertThat(factory.getRelyingParty().getName()).isEqualTo("Gravitee.io Access Management");
        assertThat(factory.getRelyingParty().getId()).isEqualTo("localhost");
    }
}
