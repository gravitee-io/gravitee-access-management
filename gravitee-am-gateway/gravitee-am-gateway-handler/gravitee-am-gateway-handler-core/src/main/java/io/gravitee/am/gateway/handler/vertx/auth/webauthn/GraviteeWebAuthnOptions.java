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

import io.vertx.ext.auth.webauthn.WebAuthnOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeWebAuthnOptions extends WebAuthnOptions {
    private static Logger LOGGER = LoggerFactory.getLogger(GraviteeWebAuthnOptions.class);

    @Override
    public WebAuthnOptions putRootCertificate(String key, String value) {
        try {
            return super.putRootCertificate(key, value);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Root Certificate {} can't be loaded due to {}, please update the certificate.", key, e.getMessage(), e);
            return this;
        }
    }
}
