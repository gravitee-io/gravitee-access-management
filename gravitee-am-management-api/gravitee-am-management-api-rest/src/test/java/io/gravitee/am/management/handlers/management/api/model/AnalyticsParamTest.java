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
package io.gravitee.am.management.handlers.management.api.model;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalyticsParamTest {

    private static final long HOUR = ChronoUnit.HOURS.getDuration().toMillis();

    private AnalyticsParam validParam() {
        AnalyticsParam param = new AnalyticsParam();
        param.setType(new AnalyticsTypeParam("count"));
        param.setFrom(1000);
        param.setTo(1000 + HOUR);
        param.setInterval(HOUR);
        return param;
    }

    private String messageOf(AnalyticsParam param) {
        return assertThrows(BadRequestException.class, param::validate).getMessage();
    }

    @Test
    void shouldAcceptAValidQuery() {
        assertDoesNotThrow(validParam()::validate);
    }

    @Test
    void shouldRejectAMissingType() {
        AnalyticsParam param = validParam();
        param.setType(null);

        assertEquals("Query parameter 'type' is not valid", messageOf(param));
    }

    @Test
    void shouldRejectAnUnrecognisedType() {
        AnalyticsParam param = validParam();
        param.setType(new AnalyticsTypeParam("not-a-type"));

        assertEquals("Query parameter 'type' is not valid", messageOf(param));
    }

    @Test
    void shouldRejectAnUnparsableFrom() {
        AnalyticsParam param = validParam();
        param.setFrom(-1L);

        assertEquals("Query parameter 'from' is not valid", messageOf(param));
    }

    @Test
    void shouldRejectAnUnparsableTo() {
        AnalyticsParam param = validParam();
        param.setTo(-1L);

        assertEquals("Query parameter 'to' is not valid", messageOf(param));
    }

    @Test
    void shouldRejectAnUnparsableInterval() {
        AnalyticsParam param = validParam();
        param.setInterval(-1L);

        assertEquals("Query parameter 'interval' is not valid", messageOf(param));
    }

    @Test
    void shouldRejectAnIntervalOutsideItsBounds() {
        AnalyticsParam param = validParam();
        param.setInterval(ChronoUnit.YEARS.getDuration().toMillis() * 2);

        assertEquals("Query parameter 'interval' is not valid. 'interval' must be >= 1000000 (millis) and <= 31556952 (years)",
                messageOf(param));
    }

    @Test
    void shouldRejectATimeWindowThatDoesNotMoveForward() {
        AnalyticsParam param = validParam();
        param.setTo(param.getFrom());

        assertEquals("'from' query parameter value must be greater than 'to'", messageOf(param));
    }

    @Test
    void shouldRejectAnUnknownField() {
        AnalyticsParam param = validParam();
        param.setField("not-a-field");

        assertEquals("'field' query parameter is invalid", messageOf(param));
    }

    @Test
    void shouldAcceptAKnownField() {
        AnalyticsParam param = validParam();
        param.setField("application");

        assertDoesNotThrow(param::validate);
    }
}
