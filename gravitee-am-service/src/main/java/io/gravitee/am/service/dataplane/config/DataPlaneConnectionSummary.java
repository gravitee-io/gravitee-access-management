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
package io.gravitee.am.service.dataplane.config;

import java.util.List;

/**
 * The credential-free view of a data plane's connection settings: enough to tell which store an
 * environment points at, and nothing else.
 *
 * @param database the database the data plane reads and writes, {@code null} if it cannot be resolved
 * @param hosts    {@code host} or {@code host:port} entries, empty when they cannot be resolved
 *                 without touching the credential-bearing part of a connection uri
 *
 * @author GraviteeSource Team
 */
public record DataPlaneConnectionSummary(String database, List<String> hosts) {

    public static final DataPlaneConnectionSummary UNKNOWN = new DataPlaneConnectionSummary(null, List.of());

    public DataPlaneConnectionSummary {
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
    }
}
