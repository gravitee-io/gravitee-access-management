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

package io.gravitee.am.service.dataplane.user.activity.utils;


import io.gravitee.am.service.dataplane.user.activity.configuration.UserActivityConfiguration;
import lombok.AllArgsConstructor;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import static io.gravitee.am.service.dataplane.user.activity.utils.CoordinateUtils.computeCoordinate;
import static io.gravitee.am.service.dataplane.user.activity.utils.HashedKeyUtils.computeHash;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@AllArgsConstructor
public final class UserActivityFunctions {

    public static final String LONGITUDE_KEY = "lon";
    public static final String LATITUDE_KEY = "lat";
    public static final String USER_AGENT = "user_agent";
    public static final String LOGIN_ATTEMPTS = "login_attempts";
    public static final int LONGITUDE_BOUNDARY = 180;
    public static final int LATITUDE_BOUNDARY = 90;

    private final UserActivityConfiguration configuration;

    public String buildKey(String userId) {
        return computeHash(configuration.getAlgorithmKey(), userId, configuration.getSalt());
    }

    public Double buildLatitude(Map<String, Object> data) {
        return computeCoordinate(data, LATITUDE_KEY, configuration.getLatitudeVariation(), LATITUDE_BOUNDARY);
    }

    public Double buildLongitude(Map<String, Object> data) {
        return computeCoordinate(data, LONGITUDE_KEY, configuration.getLongitudeVariation(), LONGITUDE_BOUNDARY);
    }

    public Date getExpireAtDate(Date createdAt) {
        final ChronoUnit chronoUnit = configuration.getRetentionUnit();
        final long retentionTime = configuration.getRetentionTime();
        var expireTime = chronoUnit.getDuration().toMillis() * Math.abs(retentionTime);
        return new Date(createdAt.getTime() + expireTime);
    }
}
