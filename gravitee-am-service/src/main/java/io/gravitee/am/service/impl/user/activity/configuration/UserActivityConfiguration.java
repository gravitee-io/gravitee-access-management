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

package io.gravitee.am.service.impl.user.activity.configuration;

import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UserActivityConfiguration {

    private final boolean enabled;
    private final Algorithm algorithmKey;
    private final String salt;
    private final long retentionTime;
    private final ChronoUnit chronoUnit;
    private final double latitudeVariation;
    private final double longitudeVariation;

    public enum Algorithm {
        SHA256, SHA512, NONE
    }

    public UserActivityConfiguration(
            @Value("${user.activity.enabled:false}") boolean enabled,
            @Value("${user.activity.anon.algorithm:SHA256}") Algorithm algorithmKey,
            @Value("${user.activity.anon.salt:#{null}}") String salt,
            @Value("${user.activity.retention.time:3}") long retentionTime,
            @Value("${user.activity.retention.unit:MONTHS}") ChronoUnit chronoUnit,
            @Value("${user.activity.geolocation.variation.latitude:0.07}") double latitudeVariation,
            @Value("${user.activity.geolocation.variation.longitude:0.07}") double longitudeVariation
    ) {
        this.enabled = enabled;
        this.algorithmKey = algorithmKey;
        this.salt = salt;
        this.retentionTime = retentionTime;
        this.chronoUnit = chronoUnit;
        this.latitudeVariation = latitudeVariation;
        this.longitudeVariation = longitudeVariation;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Algorithm getAlgorithmKey() {
        return algorithmKey;
    }

    public String getSalt() {
        return salt;
    }

    public long getRetentionTime() {
        return retentionTime;
    }

    public ChronoUnit getRetentionUnit() {
        return chronoUnit;
    }

    public double getLatitudeVariation() {
        return latitudeVariation;
    }

    public double getLongitudeVariation() {
        return longitudeVariation;
    }

}
