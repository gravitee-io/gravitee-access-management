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

import io.gravitee.am.common.analytics.Field;
import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.common.audit.EventType;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.QueryParam;

import java.time.temporal.ChronoUnit;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AnalyticsParam {

    @QueryParam("from")
    @Parameter(description = "Used to define the start date of the time window to query")
    private long from;

    @QueryParam("to")
    @Parameter(description = "Used to define the end date of the time window to query")
    private long to;

    @QueryParam("interval")
    @Parameter(description = "The time interval when getting histogram data")
    private long interval;

    @QueryParam("size")
    @Parameter(description = "The number of data to retrieve")
    private int size;

    @QueryParam("type")
    @Parameter(
            description = "The type of data to retrieve (group_by, date_histo, count)",
            required = true,
            schema = @Schema(implementation = Type.class)
    )
    private AnalyticsTypeParam type;

    @QueryParam("field")
    private String field;

    public long getFrom() {
        return from;
    }

    public void setFrom(long from) {
        this.from = from;
    }

    public long getTo() {
        return to;
    }

    public void setTo(long to) {
        this.to = to;
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public Type getType() {
        return type.getValue();
    }

    public void setType(AnalyticsTypeParam type) {
        this.type = type;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public void validate() {
        if (type == null || type.getValue() == null) {
            throw new BadRequestException("Query parameter 'type' is not valid");
        }

        if (from == -1L) {
            throw new BadRequestException("Query parameter 'from' is not valid");
        }

        if (to == -1L) {
            throw new BadRequestException("Query parameter 'to' is not valid");
        }

        if (interval == -1L) {
            throw new BadRequestException("Query parameter 'interval' is not valid");
        }

        if (interval <  ChronoUnit.MILLIS.getDuration().toMillis() || interval > ChronoUnit.YEARS.getDuration().toMillis()) {
            throw new BadRequestException("Query parameter 'interval' is not valid. 'interval' must be >= 1000000 (millis) and <= 31556952 (years)");
        }

        if (from >= to) {
            throw new BadRequestException("'from' query parameter value must be greater than 'to'");
        }

        if (field != null && !field.isEmpty() && !EventType.types().contains(field.toUpperCase()) && !Field.types().contains(field.toLowerCase())) {
                throw new BadRequestException("'field' query parameter is invalid");
            }

    }
}
