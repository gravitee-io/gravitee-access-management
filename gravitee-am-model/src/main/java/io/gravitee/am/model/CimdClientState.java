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

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * Persists the hash of the monitored properties of a CIMD client's last-seen metadata document.
 * Unlike {@link CimdMetadataDocument}, this record never expires — it is used to detect
 * changes in monitored properties across cache TTL boundaries and gateway restarts.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class CimdClientState {

    private String id;
    /** The AM domain that owns this CIMD client. */
    private String domainId;
    /** The URL-shaped client_id — unique within a domain. */
    private String clientId;
    /** SHA-256 hash of the canonical representation of the monitored metadata properties. */
    private String monitoredPropertiesHash;
    private Date updatedAt;
}
