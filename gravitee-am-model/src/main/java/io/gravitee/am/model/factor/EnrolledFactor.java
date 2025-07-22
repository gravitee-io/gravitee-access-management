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
package io.gravitee.am.model.factor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enrolled factor for a specific user
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnrolledFactor {

    private String factorId;

    private String appId;

    private FactorStatus status = FactorStatus.NULL;

    private EnrolledFactorSecurity security;

    private EnrolledFactorChannel channel;

    private Boolean primary;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public EnrolledFactor() {
    }

    public EnrolledFactor(EnrolledFactor other) {
        this.factorId = other.factorId;
        this.appId = other.appId;
        this.status = other.status;
        if (other.security != null) {
            this.security = new EnrolledFactorSecurity(other.security);
        }
        this.channel = other.channel;
        this.primary = other.primary;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public String getFactorId() {
        return factorId;
    }

    public void setFactorId(String factorId) {
        this.factorId = factorId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public FactorStatus getStatus() {
        return status;
    }

    public void setStatus(FactorStatus status) {
        this.status = status;
    }

    public EnrolledFactorSecurity getSecurity() {
        return security;
    }

    public void setSecurity(EnrolledFactorSecurity security) {
        this.security = security;
    }

    public EnrolledFactorChannel getChannel() {
        return channel;
    }

    public void setChannel(EnrolledFactorChannel channel) {
        this.channel = channel;
    }

    public Boolean isPrimary() {
        return primary;
    }

    public void setPrimary(Boolean primary) {
        this.primary = primary;
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

    public static Map<String, EnrolledFactor> asTypeMap(List<EnrolledFactor> factors) {
        if(factors == null) {
            return Map.of();
        }
        final Function<EnrolledFactor, String> keyFun = f -> Optional.ofNullable(f.getChannel()).map(EnrolledFactorChannel::getType).map(Objects::toString).orElse("OTHER");
        return factors.stream()
                .collect(Collectors.toMap(keyFun, f -> f, (f1, f2) -> f1));
    }
    public static Map<String, EnrolledFactor> asIdMap(List<EnrolledFactor> factors) {
        if(factors == null) {
            return Map.of();
        }
        return factors.stream()
                .collect(Collectors.toMap(EnrolledFactor::getFactorId, f -> f, (f1, f2) -> f1));
    }

}
