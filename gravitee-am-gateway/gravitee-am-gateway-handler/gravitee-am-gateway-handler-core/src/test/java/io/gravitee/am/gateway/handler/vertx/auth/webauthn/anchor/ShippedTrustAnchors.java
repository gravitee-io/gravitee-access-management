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

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The trust anchors shipped with the gateway distribution, read from the sources of the distribution
 * module so tests run against the exact files a gateway loads by default.
 *
 * @author GraviteeSource Team
 */
public final class ShippedTrustAnchors {

    /**
     * Relative to this module, the working directory of its tests.
     */
    public static final Path FOLDER = Path.of("../../gravitee-am-gateway-standalone/gravitee-am-gateway-standalone-distribution/src/main/resources/webauthn/trust-anchors");

    private ShippedTrustAnchors() {
    }

    public static WebAuthnTrustAnchorRegistry registry() {
        if (!Files.isDirectory(FOLDER)) {
            throw new IllegalStateException("Shipped WebAuthn trust anchors not found at " + FOLDER.toAbsolutePath().normalize());
        }
        return WebAuthnTrustAnchorLoader.load(FOLDER);
    }
}
