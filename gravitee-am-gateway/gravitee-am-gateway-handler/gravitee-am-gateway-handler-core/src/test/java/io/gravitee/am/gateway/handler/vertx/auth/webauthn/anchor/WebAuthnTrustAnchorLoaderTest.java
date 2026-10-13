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

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.ANDROID_KEY;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.ANDROID_SAFETYNET;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.APPLE;
import static io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.WebAuthnTrustAnchorRegistry.MDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class WebAuthnTrustAnchorLoaderTest {

    private static final AtomicLong SERIAL = new AtomicLong();

    @TempDir
    Path folder;

    @Nested
    class Scopes {

        @Test
        void top_level_file_applies_to_the_format_it_is_named_after() throws Exception {
            X509Certificate apple = certificate("Apple");
            writePem(folder.resolve("apple.pem"), apple);

            WebAuthnTrustAnchorRegistry registry = WebAuthnTrustAnchorLoader.load(folder);

            assertThat(registry.roots(APPLE)).containsExactly(apple);
            assertThat(registry.roots(ANDROID_KEY)).isEmpty();
        }

        @Test
        void top_level_directory_applies_to_the_format_it_is_named_after() throws Exception {
            X509Certificate first = certificate("Android 1");
            X509Certificate second = certificate("Android 2");
            writePem(folder.resolve("android-key/first.pem"), first);
            writeDer(folder.resolve("android-key/second.der"), second);

            assertThat(WebAuthnTrustAnchorLoader.load(folder).roots(ANDROID_KEY)).containsExactly(first, second);
        }

        @Test
        void file_and_directory_of_the_same_format_are_merged() throws Exception {
            X509Certificate file = certificate("Android file");
            X509Certificate directory = certificate("Android directory");
            writePem(folder.resolve("android-key.pem"), file);
            writePem(folder.resolve("android-key/root.pem"), directory);

            assertThat(WebAuthnTrustAnchorLoader.load(folder).roots(ANDROID_KEY)).containsExactlyInAnyOrder(file, directory);
        }

        @Test
        void files_in_nested_directories_belong_to_the_top_level_directory() throws Exception {
            X509Certificate depth2 = certificate("Depth 2");
            X509Certificate depth3 = certificate("Depth 3");
            writePem(folder.resolve("android-key/google/root.pem"), depth2);
            writePem(folder.resolve("android-key/google/hardware/root.pem"), depth3);

            assertThat(WebAuthnTrustAnchorLoader.load(folder).roots(ANDROID_KEY)).containsExactlyInAnyOrder(depth2, depth3);
        }

        @Test
        void entries_named_after_no_format_apply_to_every_anchored_format() throws Exception {
            X509Certificate file = certificate("Corporate root");
            X509Certificate directory = certificate("Corporate directory root");
            writePem(folder.resolve("corporate.pem"), file);
            writePem(folder.resolve("corporate/root.pem"), directory);

            WebAuthnTrustAnchorRegistry registry = WebAuthnTrustAnchorLoader.load(folder);

            assertThat(registry.sharedRoots()).containsExactlyInAnyOrder(file, directory);
            assertThat(registry.roots(ANDROID_KEY)).containsExactlyInAnyOrder(file, directory);
            assertThat(registry.roots(APPLE)).containsExactlyInAnyOrder(file, directory);
            assertThat(registry.roots(ANDROID_SAFETYNET)).containsExactlyInAnyOrder(file, directory);
            assertThat(registry.roots("packed")).isEmpty();
            assertThat(registry.roots(MDS)).isEmpty();
        }

        @Test
        void scope_names_are_case_insensitive_and_u2f_means_fido_u2f() throws Exception {
            X509Certificate apple = certificate("Apple");
            X509Certificate u2f = certificate("U2F");
            writePem(folder.resolve("Apple.PEM"), apple);
            writePem(folder.resolve("u2f.pem"), u2f);

            WebAuthnTrustAnchorRegistry registry = WebAuthnTrustAnchorLoader.load(folder);

            assertThat(registry.roots(APPLE)).containsExactly(apple);
            assertThat(registry.roots("fido-u2f")).containsExactly(u2f);
        }
    }

    @Nested
    class Files_ {

        @Test
        void loads_every_supported_extension_whatever_the_case() throws Exception {
            X509Certificate pem = certificate("pem");
            X509Certificate crt = certificate("crt");
            X509Certificate cer = certificate("cer");
            X509Certificate der = certificate("der");
            writePem(folder.resolve("apple/a.PEM"), pem);
            writePem(folder.resolve("apple/b.crt"), crt);
            writePem(folder.resolve("apple/c.Cer"), cer);
            writeDer(folder.resolve("apple/d.der"), der);

            assertThat(WebAuthnTrustAnchorLoader.load(folder).roots(APPLE)).containsExactlyInAnyOrder(pem, crt, cer, der);
        }

        @Test
        void detects_der_content_whatever_the_extension() throws Exception {
            X509Certificate certificate = certificate("DER in a crt");
            writeDer(folder.resolve("apple.crt"), certificate);

            assertThat(WebAuthnTrustAnchorLoader.load(folder).roots(APPLE)).containsExactly(certificate);
        }

        @Test
        void loads_every_certificate_of_a_pem_bundle() throws Exception {
            X509Certificate first = certificate("Bundle 1");
            X509Certificate second = certificate("Bundle 2");
            Files.writeString(folder.resolve("android-key.pem"), pem(first) + pem(second));

            assertThat(WebAuthnTrustAnchorLoader.load(folder).roots(ANDROID_KEY)).containsExactly(first, second);
        }

        @Test
        void ignores_unsupported_extensions() throws Exception {
            writePem(folder.resolve("apple.txt"), certificate("txt"));
            writePem(folder.resolve("apple/root.key"), certificate("key"));
            Files.writeString(folder.resolve("README"), "trust anchors");

            assertThat(WebAuthnTrustAnchorLoader.load(folder).isEmpty()).isTrue();
        }

        @Test
        void skips_expired_and_unreadable_certificates_and_keeps_the_others() throws Exception {
            X509Certificate valid = certificate("Valid");
            writePem(folder.resolve("android-key/valid.pem"), valid);
            writePem(folder.resolve("android-key/expired.pem"), certificate("Expired", Instant.now().minus(30, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS)));
            writePem(folder.resolve("android-key/future.pem"), certificate("Not yet valid", Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS)));
            Files.writeString(folder.resolve("android-key/garbage.pem"), "-----BEGIN CERTIFICATE-----\nnot a certificate\n-----END CERTIFICATE-----\n");

            assertThat(WebAuthnTrustAnchorLoader.load(folder).roots(ANDROID_KEY)).containsExactly(valid);
        }

        @Test
        void loads_a_certificate_found_twice_once() throws Exception {
            X509Certificate certificate = certificate("Twice");
            writePem(folder.resolve("android-key/one.pem"), certificate);
            writeDer(folder.resolve("android-key/two.der"), certificate);

            assertThat(WebAuthnTrustAnchorLoader.load(folder).roots(ANDROID_KEY)).containsExactly(certificate);
        }
    }

    @Nested
    class Walk {

        @Test
        void skips_hidden_files_and_directories() throws Exception {
            writePem(folder.resolve(".apple.pem"), certificate("Hidden file"));
            writePem(folder.resolve(".hidden/apple.pem"), certificate("Hidden directory"));
            writePem(folder.resolve("android-key/.backup/root.pem"), certificate("Hidden nested directory"));

            assertThat(WebAuthnTrustAnchorLoader.load(folder).isEmpty()).isTrue();
        }

        @Test
        void loads_a_kubernetes_configmap_mount_once() throws Exception {
            // kubelet layout: real files in a hidden timestamped directory, ..data pointing to it, and one
            // symbolic link per key at the top level
            X509Certificate apple = certificate("Apple");
            writePem(folder.resolve("..2026_10_13_10_00_00.000000000/apple.pem"), apple);
            Files.createSymbolicLink(folder.resolve("..data"), Path.of("..2026_10_13_10_00_00.000000000"));
            Files.createSymbolicLink(folder.resolve("apple.pem"), Path.of("..data/apple.pem"));

            assertThat(WebAuthnTrustAnchorLoader.load(folder).roots(APPLE)).containsExactly(apple);
        }

        @Test
        void survives_a_symbolic_link_loop() throws Exception {
            X509Certificate certificate = certificate("Android");
            writePem(folder.resolve("android-key/root.pem"), certificate);
            Files.createSymbolicLink(folder.resolve("android-key/loop"), folder.resolve("android-key"));

            assertThat(WebAuthnTrustAnchorLoader.load(folder).roots(ANDROID_KEY)).containsExactly(certificate);
        }

        @Test
        void returns_an_empty_registry_when_the_folder_does_not_exist() {
            WebAuthnTrustAnchorRegistry registry = WebAuthnTrustAnchorLoader.load(folder.resolve("missing"));

            assertThat(registry.isEmpty()).isTrue();
            assertThat(registry.roots(ANDROID_KEY)).isEmpty();
        }

        @Test
        void returns_an_empty_registry_when_the_path_is_a_file() throws Exception {
            Path file = folder.resolve("apple.pem");
            writePem(file, certificate("Apple"));

            assertThat(WebAuthnTrustAnchorLoader.load(file).isEmpty()).isTrue();
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static X509Certificate certificate(String commonName) throws Exception {
        return certificate(commonName, Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(365, ChronoUnit.DAYS));
    }

    private static X509Certificate certificate(String commonName, Instant notBefore, Instant notAfter) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        X500Name name = new X500Name("CN=" + commonName + ", O=Gravitee");
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(SERIAL.incrementAndGet()),
                Date.from(notBefore), Date.from(notAfter), name, keyPair.getPublic());
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate())));
    }

    private static String pem(X509Certificate certificate) throws Exception {
        return "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(certificate.getEncoded())
                + "\n-----END CERTIFICATE-----\n";
    }

    private static void writePem(Path file, X509Certificate certificate) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, pem(certificate));
    }

    private static void writeDer(Path file, X509Certificate certificate) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, certificate.getEncoded());
    }
}
