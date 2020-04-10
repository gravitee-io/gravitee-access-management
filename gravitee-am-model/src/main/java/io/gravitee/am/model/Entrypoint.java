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
import java.util.List;
import java.util.Objects;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Entrypoint {

    private String id;
    private String name;
    private String description;
    private String url;
    private List<String> tags;
    private String organizationId;
    private boolean defaultEntrypoint;
    private Date createdAt;
    private Date updatedAt;

    public Entrypoint(){}

    public Entrypoint(Entrypoint other) {
        this.id = other.id;
        this.name = other.name;
        this.description = other.description;
        this.url = other.url;
        this.tags = other.tags;
        this.organizationId = other.organizationId;
        this.defaultEntrypoint = other.defaultEntrypoint;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entrypoint)) return false;
        Entrypoint that = (Entrypoint) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(url, that.url) &&
                Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, url, tags);
    }

    @Override
    public String toString() {
        return "EntrypointEntity{" +
                "id='" + id + '\'' +
                ", value='" + url + '\'' +
                ", tags=" + tags +
                '}';
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDefaultEntrypoint() {
        return defaultEntrypoint;
    }

    public void setDefaultEntrypoint(boolean defaultEntrypoint) {
        this.defaultEntrypoint = defaultEntrypoint;
    }
}
