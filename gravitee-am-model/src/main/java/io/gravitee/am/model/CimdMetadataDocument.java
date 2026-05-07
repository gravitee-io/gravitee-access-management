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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.Date;

/**
 * A cached Client ID Metadata Document fetched from a URL-shaped client_id.
 * Scoped to a domain; clientId (the fetch URL) is unique within a domain.
 * 
 * @author GraviteeSource Team
 */
@Setter
@Getter
public class CimdMetadataDocument {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String id;
    private String domainId;
    /** The URL used as the client_id — also acts as the cache key within the domain. */
    private String clientId;
    /** Raw JSON body returned by the metadata endpoint. */
    private String metadata;
    private Date fetchedAt;
    /** Wall-clock expiry; computed as fetchedAt + effective TTL. */
    private Date expiresAt;
    private Date updatedAt;

    public String getLogoUri() {
        return getStringFromMetadata("logo_uri");
    }

    public String getClientName() {
        return getStringFromMetadata("client_name");
    }

    private String getStringFromMetadata(String key) {
        if (metadata == null) {
            return null;
        }
        try {
            return MAPPER.readTree(metadata).path(key).asText(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.before(new Date());
    }

    public static CimdMetadataDocument of(String domainId, String clientId, String metadata, Duration ttl) {
        final Date now = new Date();
        final CimdMetadataDocument doc = new CimdMetadataDocument();
        doc.setDomainId(domainId);
        doc.setClientId(clientId);
        doc.setMetadata(metadata);
        doc.setFetchedAt(now);
        doc.setUpdatedAt(now);
        doc.setExpiresAt(new Date(now.getTime() + ttl.toMillis()));
        return doc;
    }
}
