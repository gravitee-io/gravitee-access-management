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
import io.swagger.annotations.ApiParam;

import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AnalyticsParam {

    @QueryParam("from")
    @ApiParam(value = "Used to define the start date of the time window to query")
    private long from;

    @QueryParam("to")
    @ApiParam(value = "Used to define the end date of the time window to query")
    private long to;

    @QueryParam("interval")
    @ApiParam(value = "The time interval when getting histogram data")
    private long interval;

    @QueryParam("size")
    @ApiParam(value = "The number of data to retrieve")
    private int size;

    @QueryParam("type")
    @ApiParam(
            value = "The type of data to retrieve",
            required = true,
            allowableValues = "group_by,date_histo,count"
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

    public void validate() throws WebApplicationException {
        if (type.getValue() == null) {
            throw new WebApplicationException(Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'type' is not valid")
                    .build());
        }

        if (from == -1) {
            throw new WebApplicationException(Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'from' is not valid")
                    .build());
        }

        if (to == -1) {
            throw new WebApplicationException(Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'to' is not valid")
                    .build());
        }

        if (interval == -1) {
            throw new WebApplicationException(Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'interval' is not valid")
                    .build());
        }

        if (interval < 1_000 || interval > 1_000_000_000) {
            throw new WebApplicationException(Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'interval' is not valid. 'interval' must be >= 1000 and <= 1000000000")
                    .build());
        }

        if (from >= to) {
            throw new WebApplicationException(Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("'from' query parameter value must be greater than 'to'")
                    .build());
        }

        if (field != null && !field.isEmpty()) {
            // check if field is valid
            if (!EventType.types().contains(field.toUpperCase()) && !Field.types().contains(field.toLowerCase())) {
                throw new WebApplicationException(Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity("'field' query parameter is invalid")
                        .build());
            }
        }
    }
}
