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
package io.gravitee.am.repository.mongodb.management.internal.model;

import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Date;
import java.util.Objects;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NotificationAcknowledgeMongo {

    @BsonId
    private String id;

    private Date createdAt;

    private Date updatedAt;

    private String resourceId;

    private String resourceType;

    private String audienceId;

    private String type;

    private int counter;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getAudienceId() {
        return audienceId;
    }

    public void setAudienceId(String audienceId) {
        this.audienceId = audienceId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationAcknowledgeMongo)) return false;
        NotificationAcknowledgeMongo that = (NotificationAcknowledgeMongo) o;
        return Objects.equals(id, that.id) && Objects.equals(resourceId, that.resourceId) && Objects.equals(resourceType, that.resourceType) && Objects.equals(audienceId, that.audienceId) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, resourceId, resourceType, audienceId, type);
    }
}
