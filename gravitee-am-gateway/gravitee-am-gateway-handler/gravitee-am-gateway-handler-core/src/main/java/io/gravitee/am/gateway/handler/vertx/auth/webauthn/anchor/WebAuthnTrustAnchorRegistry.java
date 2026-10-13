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

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Root certificates trusted to anchor WebAuthn attestation certificate chains, per attestation format.
 * Loaded once per gateway by {@link WebAuthnTrustAnchorLoader} from the trust anchors folder of the
 * distribution and shared by every security domain; the certificates a domain declares in its WebAuthn
 * settings come on top of these.
 *
 * @author GraviteeSource Team
 */
public final class WebAuthnTrustAnchorRegistry {

    public static final String ANDROID_KEY = "android-key";
    public static final String ANDROID_SAFETYNET = "android-safetynet";
    public static final String APPLE = "apple";
    public static final String MDS = "mds";

    /**
     * The scopes a trust anchor file can be attached to: the attestation formats, plus {@code mds} for the
     * FIDO Metadata Service.
     */
    public static final Set<String> SCOPES = Set.of("none", "fido-u2f", "packed", "tpm", ANDROID_KEY, ANDROID_SAFETYNET, APPLE, MDS);

    /**
     * The attestation formats whose certificate chains are validated against a trusted root. The roots of
     * the shared pool apply to each of them.
     */
    public static final Set<String> ANCHORED_FORMATS = Set.of(ANDROID_KEY, ANDROID_SAFETYNET, APPLE);

    private static final WebAuthnTrustAnchorRegistry EMPTY = new WebAuthnTrustAnchorRegistry(Map.of(), List.of());

    private final Map<String, List<X509Certificate>> rootsByScope;
    private final List<X509Certificate> sharedRoots;

    WebAuthnTrustAnchorRegistry(Map<String, List<X509Certificate>> rootsByScope, List<X509Certificate> sharedRoots) {
        this.rootsByScope = Map.copyOf(rootsByScope);
        this.sharedRoots = List.copyOf(sharedRoots);
    }

    public static WebAuthnTrustAnchorRegistry empty() {
        return EMPTY;
    }

    /**
     * @param scope an attestation format, or {@code mds}
     * @return the roots loaded for this scope, followed for an anchored format by the roots of the shared
     * pool; empty when none is known
     */
    public List<X509Certificate> roots(String scope) {
        List<X509Certificate> roots = new ArrayList<>(rootsByScope.getOrDefault(scope, List.of()));
        if (ANCHORED_FORMATS.contains(scope)) {
            sharedRoots.stream().filter(root -> !roots.contains(root)).forEach(roots::add);
        }
        return roots;
    }

    /**
     * The roots loaded from files not named after a scope, applied to every anchored format.
     */
    public List<X509Certificate> sharedRoots() {
        return sharedRoots;
    }

    public boolean isEmpty() {
        return rootsByScope.isEmpty() && sharedRoots.isEmpty();
    }
}
