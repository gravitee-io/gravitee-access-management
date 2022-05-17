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

import java.util.Map;
import org.junit.Test;

import static io.gravitee.am.service.impl.user.activity.utils.CoordinateUtils.computeCoordinate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CoordinateUtilsTest {

    @Test
    public void must_return_null_when_data_not_present() {
        assertNull(computeCoordinate(Map.of(), "lat", 0.07, 90));
        assertNull(computeCoordinate(Map.of(), "lon", 0.07, 180));
    }

    @Test
    public void must_return_IP_between_range() {
        final double ogLat = 50.62925D;
        final double ogLon = 3.057256;
        final Map<String, Object> data = Map.of("lat", ogLat, "lon", ogLon);

        assertCoordinate(ogLat, data, 90, "lat", 0.07);
        assertCoordinate(ogLon, data, 180, "lon", 0.07);
    }

    @Test
    public void must_return_IP_between_range_negative_delta() {
        final double ogLat = 50.62925D;
        final double ogLon = 3.057256;
        final Map<String, Object> data = Map.of("lat", ogLat, "lon", ogLon);

        assertCoordinate(ogLat, data, 90, "lat", Math.abs(-0.07));
        assertCoordinate(ogLon, data, 180, "lon", Math.abs(-0.07));
    }

    @Test
    public void must_return_IP_between_range_edge_latitude_negative() {
        final double ogLat = -90;
        final double ogLon = -180;
        final Map<String, Object> data = Map.of("lat", ogLat, "lon", ogLon);

        assertCoordinate(ogLat, data, 90, "lat", 0.07);
        assertCoordinate(ogLon, data, 180, "lon", 0.07);
    }

    @Test
    public void must_return_IP_between_range_edge_latitude_positive() {
        final double ogLat = 90;
        final double ogLon = 180;
        final Map<String, Object> data = Map.of("lat", ogLat, "lon", ogLon);

        assertCoordinate(ogLat, data, 90, "lat", 0.07);
        assertCoordinate(ogLon, data, 180, "lon", 0.07);
    }

    @Test
    public void must_return_exact_location() {
        final double ogLat = 50.62925D;
        final double ogLon = 3.057256;
        final Map<String, Object> data = Map.of("lat", ogLat, "lon", ogLon);

        assertCoordinate(ogLat, data, 90, "lat", 0);
        assertCoordinate(ogLon, data, 180, "lon", 0);
    }

    private void assertCoordinate(double exactCoordinate, Map<String, Object> data, int boundary, String key, double delta) {
        final Double randomizedCoordinate = computeCoordinate(data, key, delta, boundary);
        assertNotNull(randomizedCoordinate);
        assertEquals(randomizedCoordinate, exactCoordinate, 2 * delta);
    }
}
