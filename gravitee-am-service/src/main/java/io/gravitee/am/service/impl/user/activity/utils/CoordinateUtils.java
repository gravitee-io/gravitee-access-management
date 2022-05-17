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

package io.gravitee.am.service.impl.user.activity.utils;

import java.security.SecureRandom;
import java.util.Map;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CoordinateUtils {

    private static final SecureRandom random = new SecureRandom();

    private CoordinateUtils(){}

    public static Double computeCoordinate(Map<String, Object> data, String key, double delta, int boundary) {
        if (!data.containsKey(key)) {
            return null;
        }
        final Double coordinate = (Double) data.get(key);

        if (delta == 0) {
            return coordinate;
        }

        final double safeDelta = Math.abs(delta);
        double lowerBound = coordinate - safeDelta;
        double upperBound = coordinate + safeDelta;


        if (lowerBound <= -boundary) {
            lowerBound = -boundary;
        }

        if (upperBound >= boundary) {
            upperBound = boundary;
        }

        double randomValue = lowerBound + (upperBound - lowerBound) * random.nextDouble();
        return Math.round(100D * randomValue) / 100D;
    }
}
