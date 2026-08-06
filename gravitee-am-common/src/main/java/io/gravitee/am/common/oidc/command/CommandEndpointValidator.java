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
package io.gravitee.am.common.oidc.command;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Shared save-time validation of the command_endpoint client metadata: it is a static
 * URL (no Expression Language) because it is also the <code>aud</code> of every command
 * token, and it must be an absolute HTTPS URI without fragment. Used by every
 * registration path (console/Management API, DCR, CIMD).
 *
 * @author GraviteeSource Team
 */
public final class CommandEndpointValidator {

    private CommandEndpointValidator() {
    }

    /**
     * @param commandEndpoint the endpoint to validate, may be null or blank (not registered)
     * @throws IllegalArgumentException if the endpoint is not an absolute URI or contains a fragment
     */
    public static void validate(String commandEndpoint) {
        if (commandEndpoint == null || commandEndpoint.isBlank()) {
            return;
        }
        final URI uri;
        try {
            uri = new URI(commandEndpoint);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("command_endpoint: " + commandEndpoint + " is malformed");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("command_endpoint must be an absolute URI");
        }
        if (uri.getFragment() != null) {
            throw new IllegalArgumentException("command_endpoint with fragment is forbidden");
        }
    }
}
