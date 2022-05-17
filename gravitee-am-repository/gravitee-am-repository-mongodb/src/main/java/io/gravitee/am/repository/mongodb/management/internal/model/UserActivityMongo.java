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

import java.util.Date;
import org.bson.codecs.pojo.annotations.BsonId;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserActivityMongo {

    @BsonId
    private String id;
    private String userActivityKey;

    private Double latitude;
    private Double longitude;
    private String userAgent;
    private Integer loginAttempts;

    private String userActivityType;
    private String referenceType;
    private String referenceId;

    private Date createdAt;
    private Date expireAt;

    public String getId() {
        return id;
    }

    public UserActivityMongo setId(String id) {
        this.id = id;
        return this;
    }

    public String getUserActivityKey() {
        return userActivityKey;
    }

    public UserActivityMongo setUserActivityKey(String key) {
        this.userActivityKey = key;
        return this;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UserActivityMongo setReferenceType(String referenceType) {
        this.referenceType = referenceType;
        return this;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public UserActivityMongo setReferenceId(String referenceId) {
        this.referenceId = referenceId;
        return this;
    }

    public Double getLatitude() {
        return latitude;
    }

    public UserActivityMongo setLatitude(Double latitude) {
        this.latitude = latitude;
        return this;
    }

    public Double getLongitude() {
        return longitude;
    }

    public UserActivityMongo setLongitude(Double longitude) {
        this.longitude = longitude;
        return this;
    }

    public String getUserActivityType() {
        return userActivityType;
    }

    public UserActivityMongo setUserActivityType(String userActivityType) {
        this.userActivityType = userActivityType;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public UserActivityMongo setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public UserActivityMongo setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Date getExpireAt() {
        return expireAt;
    }

    public UserActivityMongo setExpireAt(Date expireAt) {
        this.expireAt = expireAt;
        return this;
    }

    public Integer getLoginAttempts() {
        return loginAttempts;
    }

    public UserActivityMongo setLoginAttempts(Integer loginAttempts) {
        this.loginAttempts = loginAttempts;
        return this;
    }
}
