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
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStore;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.vertx.ext.auth.webauthn.WebAuthn;
import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebAuthnFactoryResilienceTest {

    private static final String ANDROID_KEY = "android-key";

    /** Not parseable as X.509. Takes the same catch as an expired certificate. */
    private static final String UNLOADABLE_CERTIFICATE = "not-a-certificate";

    @InjectMocks
    private WebAuthnFactory webAuthnFactory = new WebAuthnFactory();

    @Mock
    private Vertx vertx;

    @Mock
    private Domain domain;

    @Mock
    private RepositoryCredentialStore credentialStore;

    @BeforeEach
    void init() {
        when(vertx.getDelegate()).thenReturn(Vertx.vertx().getDelegate());
        ReflectionTestUtils.setField(webAuthnFactory, "trustAnchorRegistry", ShippedTrustAnchors.registry());
    }

    private static List<X509Certificate> androidRootsOf(GraviteeWebAuthnOptions options) {
        return options.getAdditionalRootCertificate(ANDROID_KEY);
    }

    @Nested
    @DisplayName("Loading certificates into GraviteeWebAuthnOptions")
    class LoadingCertificates {

        @Test
        void shouldRegisterEveryShippedAndroidRoot() {
            assertThat(androidRootsOf(new GraviteeWebAuthnOptions(ShippedTrustAnchors.registry())))
                    .as("Every Android Key trust anchor shipped with the gateway must be available to the "
                            + "attestation check, or devices enrolled under the missing root are refused.")
                    .containsExactlyElementsOf(ShippedTrustAnchors.registry().roots(ANDROID_KEY));
        }

        @Test
        void shouldBuildOptionsWithoutAnyTrustAnchor() {
            assertThatCode(() -> new GraviteeWebAuthnOptions(WebAuthnTrustAnchorRegistry.empty()))
                    .as("A gateway without trust anchor files must still build the options. If it does not, the "
                            + "webAuthn bean fails, rootProvider cannot be built and no security domain deploys.")
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldTolerateAnAdditionalRootCertificateThatCannotBeLoaded() {
            GraviteeWebAuthnOptions options = new GraviteeWebAuthnOptions(ShippedTrustAnchors.registry());
            List<X509Certificate> before = List.copyOf(androidRootsOf(options));

            assertThatCode(() -> options.pushAdditionalRootCertificate(ANDROID_KEY, UNLOADABLE_CERTIFICATE))
                    .as("An unloadable additional root must be skipped, not propagated.")
                    .doesNotThrowAnyException();

            assertThat(androidRootsOf(options))
                    .as("The certificates that did load must be left intact when a later one is rejected.")
                    .containsExactlyElementsOf(before);
        }

        @Test
        void shouldKeepShippedRootsWhenTheDomainSuppliesACertificateThatCannotBeLoaded() {
            GraviteeWebAuthnOptions options = new GraviteeWebAuthnOptions(ShippedTrustAnchors.registry());
            List<X509Certificate> before = List.copyOf(androidRootsOf(options));

            options.putRootCertificate("customer-root", UNLOADABLE_CERTIFICATE);

            assertThat(androidRootsOf(options))
                    .as("One bad domain-supplied certificate must not cost the roots shipped with the product.")
                    .containsExactlyElementsOf(before);
        }
    }

    @Nested
    @DisplayName("Building the webAuthn bean through WebAuthnFactory")
    class BuildingTheBean {

        @Test
        void shouldBuildWebAuthnWhenTheDomainSuppliesACertificateThatCannotBeLoaded() {
            WebAuthnSettings settings = new WebAuthnSettings();
            settings.setCertificates(Map.of("customer-root", UNLOADABLE_CERTIFICATE));
            when(domain.getWebAuthnSettings()).thenReturn(settings);

            WebAuthn webAuthn = webAuthnFactory.getObject();

            assertThat(webAuthn)
                    .as("A malformed certificate in a domain's WebAuthn settings must not fail the webAuthn "
                            + "bean, which every security domain on the gateway depends on.")
                    .isNotNull();
        }
    }
}
