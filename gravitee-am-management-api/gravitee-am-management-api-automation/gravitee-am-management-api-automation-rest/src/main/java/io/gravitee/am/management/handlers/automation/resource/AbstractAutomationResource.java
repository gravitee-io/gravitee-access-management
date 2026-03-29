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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.identityprovider.api.User;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Base class for Automation API resources.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
public abstract class AbstractAutomationResource {

    @Context
    protected SecurityContext securityContext;

    protected User getAuthenticatedUser() {
        if (securityContext.getUserPrincipal() != null) {
            return (User) ((UsernamePasswordAuthenticationToken) securityContext.getUserPrincipal()).getPrincipal();
        }
        return null;
    }
}
