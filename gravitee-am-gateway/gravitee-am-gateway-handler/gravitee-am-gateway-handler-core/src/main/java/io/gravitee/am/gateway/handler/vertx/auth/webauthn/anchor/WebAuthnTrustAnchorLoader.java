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

import lombok.CustomLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds the {@link WebAuthnTrustAnchorRegistry} from a folder of certificate files.
 * <ul>
 *     <li>Every file with a supported extension ({@code .pem}, {@code .crt}, {@code .cer}, {@code .der}, any
 *     case) is loaded, PEM (one certificate or a bundle) or DER, whatever the extension. Other files are
 *     ignored.</li>
 *     <li>Directories are scanned recursively, up to {@value #MAX_DEPTH} levels, following symbolic links.
 *     Entries whose name starts with a dot are skipped, which keeps the {@code ..data} directory of a
 *     Kubernetes ConfigMap or Secret mount from loading every certificate twice.</li>
 *     <li>The scope of a certificate is the first path element under the folder: the base name of a file
 *     at the top level ({@code apple.pem}), or the name of a top-level directory for every file below it
 *     ({@code android-key/google/2019.pem}). A name that is not a known scope sends its roots to the
 *     shared pool, applied to every anchored format.</li>
 *     <li>Expired, not yet valid or unreadable certificates are logged and skipped; a certificate found
 *     twice is loaded once.</li>
 * </ul>
 * A missing folder is not an error: the gateway starts without trust anchors and says so.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public final class WebAuthnTrustAnchorLoader {

    static final int MAX_DEPTH = 10;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pem", ".crt", ".cer", ".der");
    private static final Map<String, String> SCOPE_ALIASES = Map.of("u2f", "fido-u2f");

    private WebAuthnTrustAnchorLoader() {
    }

    public static WebAuthnTrustAnchorRegistry load(Path folder) {
        if (folder == null || !Files.isDirectory(folder) || !Files.isReadable(folder)) {
            log.warn("No WebAuthn trust anchor loaded: {} is not a readable directory. Android Key, Apple and Android SafetyNet "
                    + "attestations will be rejected unless the domain provides its own certificates.", folder);
            return WebAuthnTrustAnchorRegistry.empty();
        }

        Map<String, Map<String, X509Certificate>> rootsByScope = new TreeMap<>();
        Map<String, X509Certificate> sharedRoots = new LinkedHashMap<>();
        for (Path file : certificateFiles(folder)) {
            String scope = scopeOf(folder.relativize(file));
            Map<String, X509Certificate> target = scope == null ? sharedRoots : rootsByScope.computeIfAbsent(scope, s -> new LinkedHashMap<>());
            for (X509Certificate certificate : read(file)) {
                if (isValid(certificate, file) && target.putIfAbsent(fingerprint(certificate), certificate) == null) {
                    log.info("WebAuthn trust anchor loaded for {}: {} (expires {}) from {}",
                            scope == null ? "every anchored format" : scope,
                            certificate.getSubjectX500Principal(), certificate.getNotAfter().toInstant(), file);
                }
            }
        }

        Map<String, List<X509Certificate>> roots = new TreeMap<>();
        rootsByScope.forEach((scope, certificates) -> roots.put(scope, List.copyOf(certificates.values())));
        WebAuthnTrustAnchorRegistry registry = new WebAuthnTrustAnchorRegistry(roots, List.copyOf(sharedRoots.values()));
        WebAuthnTrustAnchorRegistry.ANCHORED_FORMATS.stream()
                .filter(format -> registry.roots(format).isEmpty())
                .sorted()
                .forEach(format -> log.warn("No WebAuthn trust anchor loaded for {} from {}: {} attestations will be rejected unless "
                        + "the domain provides its own certificates.", format, folder, format));
        return registry;
    }

    private static List<Path> certificateFiles(Path folder) {
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(folder, EnumSet.of(FileVisitOption.FOLLOW_LINKS), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return !dir.equals(folder) && isHidden(dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile() || isHidden(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (hasSupportedExtension(file)) {
                        files.add(file);
                    } else {
                        log.debug("Ignoring {}: not a WebAuthn trust anchor file (supported extensions: {})", file, SUPPORTED_EXTENSIONS);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    if (e instanceof FileSystemLoopException) {
                        log.warn("Ignoring {}: symbolic link loop", file);
                    } else {
                        log.warn("Unable to read WebAuthn trust anchor {}", file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Unable to scan the WebAuthn trust anchor folder {}", folder, e);
        }
        files.sort(null);
        return files;
    }

    /**
     * @return the scope named by the first element of the path, or null when it does not name one
     */
    private static String scopeOf(Path relativePath) {
        String name = relativePath.getName(0).toString();
        if (relativePath.getNameCount() == 1) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        name = name.toLowerCase(Locale.ROOT);
        name = SCOPE_ALIASES.getOrDefault(name, name);
        if (WebAuthnTrustAnchorRegistry.SCOPES.contains(name)) {
            return name;
        }
        log.info("{} is not named after a WebAuthn attestation format: its certificates apply to every anchored format", relativePath);
        return null;
    }

    private static List<X509Certificate> read(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            List<X509Certificate> certificates = new ArrayList<>();
            for (Certificate certificate : CertificateFactory.getInstance("X.509").generateCertificates(input)) {
                certificates.add((X509Certificate) certificate);
            }
            if (certificates.isEmpty()) {
                log.warn("WebAuthn trust anchor file {} contains no certificate", file);
            }
            return certificates;
        } catch (CertificateException | IOException e) {
            log.warn("WebAuthn trust anchor file {} can't be loaded due to {}, please update the certificate.", file, e.getMessage());
            return List.of();
        }
    }

    private static boolean isValid(X509Certificate certificate, Path file) {
        try {
            certificate.checkValidity();
            return true;
        } catch (CertificateException e) {
            log.warn("WebAuthn trust anchor {} from {} can't be loaded due to {}, please update the certificate.",
                    certificate.getSubjectX500Principal(), file, e.getMessage());
            return false;
        }
    }

    private static String fingerprint(X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isHidden(Path path) {
        return path.getFileName().toString().startsWith(".");
    }

    private static boolean hasSupportedExtension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && SUPPORTED_EXTENSIONS.contains(name.substring(dot));
    }
}
