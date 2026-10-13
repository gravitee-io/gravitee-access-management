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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.parity;

import com.webauthn4j.test.CACertificatePath;
import com.webauthn4j.test.TestAttestationUtil;
import com.webauthn4j.test.authenticator.webauthn.AndroidKeyAuthenticator;
import com.webauthn4j.test.authenticator.webauthn.PackedAuthenticator;

import java.util.List;

/**
 * Attested authenticators whose attestation statement carries a chosen x5c shape. The attestation
 * certificate is always issued by the webauthn4j 3tier test intermediate CA.
 *
 * @author GraviteeSource Team
 */
final class ChainShapeAuthenticators {

    enum Shape {
        /** x5c = [leaf], as most security keys send it */
        LEAF_ONLY,
        /** x5c = [leaf, intermediate], the root left to the relying party */
        UP_TO_INTERMEDIATE,
        /** x5c = [leaf, intermediate, self-signed root] */
        UP_TO_ROOT;

        CACertificatePath caCertificatePath(CACertificatePath issuerPath) {
            return switch (this) {
                case LEAF_ONLY -> new CACertificatePath();
                case UP_TO_INTERMEDIATE -> issuerPath;
                case UP_TO_ROOT -> new CACertificatePath(List.of(
                        TestAttestationUtil.load3tierTestIntermediateCACertificate(),
                        TestAttestationUtil.load3tierTestRootCACertificate()));
            };
        }
    }

    private ChainShapeAuthenticators() {
    }

    static final class Packed extends PackedAuthenticator {
        private final Shape shape;

        Packed(Shape shape) {
            this.shape = shape;
        }

        @Override
        public CACertificatePath getCACertificatePath() {
            return shape.caCertificatePath(super.getCACertificatePath());
        }
    }

    static final class AndroidKey extends AndroidKeyAuthenticator {
        private final Shape shape;

        AndroidKey(Shape shape) {
            this.shape = shape;
        }

        @Override
        public CACertificatePath getCACertificatePath() {
            return shape.caCertificatePath(super.getCACertificatePath());
        }
    }
}
