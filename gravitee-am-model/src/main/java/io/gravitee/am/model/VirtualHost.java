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
package io.gravitee.am.model;

import io.gravitee.am.common.utils.PathUtils;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Schema(title = "Virtual host", description = "A host and path the domain is exposed on. The host and path " +
        "combination must be unique across all domains.")
public class VirtualHost {

    @Schema(description = "Hostname the domain is served on.", example = "auth.example.com")
    private String host;

    @Schema(description = "Context path the domain is served under on this host.", example = "/customers")
    private String path;

    @Schema(description = "Whether this virtual host overrides the organization entry point.",
            defaultValue = "false")
    private boolean overrideEntrypoint;

    @Override
    public String toString() {
        return "host: " + host + ", path: " + path;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPath() {
        return PathUtils.sanitize(path);
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isOverrideEntrypoint() {
        return overrideEntrypoint;
    }

    public void setOverrideEntrypoint(boolean overrideEntrypoint) {
        this.overrideEntrypoint = overrideEntrypoint;
    }
}
