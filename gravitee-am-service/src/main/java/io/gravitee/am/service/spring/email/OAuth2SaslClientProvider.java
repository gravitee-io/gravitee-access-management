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

import java.security.Provider;
import java.security.Security;

/**
 * SASL Provider for OAuth2 authentication mechanism (XOAUTH2).
 *
 * This provider registers the OAuth2SaslClient factory with the Java
 * security framework, enabling JavaMail to use XOAUTH2 authentication.
 *
 * @author GraviteeSource Team
 */
public class OAuth2SaslClientProvider extends Provider {

    private static final long serialVersionUID = 1L;
    private static final String PROVIDER_NAME = "Gravitee-OAuth2-Provider";
    private static final double PROVIDER_VERSION = 1.0;
    private static final String PROVIDER_INFO = "Gravitee OAuth2 SASL Provider";

    public OAuth2SaslClientProvider() {
        super(PROVIDER_NAME, PROVIDER_VERSION, PROVIDER_INFO);
        put("SaslClientFactory.XOAUTH2",
            "io.gravitee.am.service.spring.email.OAuth2SaslClientFactory");
    }

    /**
     * Register this provider with the Java security framework.
     * This should be called once during application startup.
     */
    public static synchronized void register() {
        if (Security.getProvider(PROVIDER_NAME) == null) {
            Security.addProvider(new OAuth2SaslClientProvider());
        }
    }

    /**
     * Unregister this provider (useful for testing).
     */
    public static synchronized void unregister() {
        Security.removeProvider(PROVIDER_NAME);
    }
}
