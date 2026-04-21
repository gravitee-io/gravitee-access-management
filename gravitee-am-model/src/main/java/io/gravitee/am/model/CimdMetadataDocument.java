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

import java.util.Date;

/**
 * A cached Client ID Metadata Document fetched from a URL-shaped client_id.
 * Scoped to a domain; clientId (the fetch URL) is unique within a domain.
 * 
 * @author GraviteeSource Team
 */
public class CimdMetadataDocument {

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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDomainId() { return domainId; }
    public void setDomainId(String domainId) { this.domainId = domainId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Date getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Date fetchedAt) { this.fetchedAt = fetchedAt; }

    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.before(new Date());
    }
}
