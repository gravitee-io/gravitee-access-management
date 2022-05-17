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

package io.gravitee.am.repository.jdbc.management.api.model;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("user_activities")
public class JdbcUserActivity {

    @Id
    private String id;

    @Column("reference_type")
    private String referenceType;

    @Column("reference_id")
    private String referenceId;

    @Column("user_activity_type")
    private String userActivityType;

    @Column("user_activity_key")
    private String userActivityKey;

    private Double latitude;
    private Double longitude;

    @Column("user_agent")
    private String userAgent;

    @Column("login_attempts")
    private Integer loginAttempts;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("expire_at")
    private LocalDateTime expireAt;

    public String getId() {
        return id;
    }

    public JdbcUserActivity setId(String id) {
        this.id = id;
        return this;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public JdbcUserActivity setReferenceType(String referenceType) {
        this.referenceType = referenceType;
        return this;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public JdbcUserActivity setReferenceId(String referenceId) {
        this.referenceId = referenceId;
        return this;
    }

    public String getUserActivityKey() {
        return userActivityKey;
    }

    public JdbcUserActivity setUserActivityKey(String userActivityKey) {
        this.userActivityKey = userActivityKey;
        return this;
    }

    public String getUserActivityType() {
        return userActivityType;
    }

    public JdbcUserActivity setUserActivityType(String userActivityType) {
        this.userActivityType = userActivityType;
        return this;
    }

    public Double getLatitude() {
        return latitude;
    }

    public JdbcUserActivity setLatitude(Double latitude) {
        this.latitude = latitude;
        return this;
    }

    public Double getLongitude() {
        return longitude;
    }

    public JdbcUserActivity setLongitude(Double longitude) {
        this.longitude = longitude;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public JdbcUserActivity setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public JdbcUserActivity setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public JdbcUserActivity setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
        return this;
    }

    public Integer getLoginAttempts() {
        return loginAttempts;
    }

    public JdbcUserActivity setLoginAttempts(Integer loginAttempts) {
        this.loginAttempts = loginAttempts;
        return this;
    }
}
