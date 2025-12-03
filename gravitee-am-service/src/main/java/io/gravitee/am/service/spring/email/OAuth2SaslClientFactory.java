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
package io.gravitee.am.service.spring.email;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;
import java.util.Map;

/**
 * Factory for creating OAuth2 SASL clients.
 *
 * This factory is registered with the Java SASL framework and creates
 * OAuth2SaslClient instances when the XOAUTH2 mechanism is requested.
 *
 * @author GraviteeSource Team
 */
public class OAuth2SaslClientFactory implements SaslClientFactory {

    private static final String MECHANISM_NAME = "XOAUTH2";

    @Override
    public SaslClient createSaslClient(
            String[] mechanisms,
            String authorizationId,
            String protocol,
            String serverName,
            Map<String, ?> props,
            CallbackHandler cbh) throws SaslException {

        // Check if XOAUTH2 is in the requested mechanisms
        for (String mechanism : mechanisms) {
            if (MECHANISM_NAME.equals(mechanism)) {
                return new OAuth2SaslClient(authorizationId, cbh);
            }
        }
        return null;
    }

    @Override
    public String[] getMechanismNames(Map<String, ?> props) {
        return new String[]{MECHANISM_NAME};
    }
}
