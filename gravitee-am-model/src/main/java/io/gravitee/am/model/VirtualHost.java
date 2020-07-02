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

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VirtualHost {

    /**
     * The host.
     * Note: host + path need to be unique across all domains.
     */
    private String host;

    /**
     * Optional path.
     * Note: host + path need to be unique across all domains.
     */
    private String path;

    /**
     * Flag indicating if organization entry point is overridden or not.
     */
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
