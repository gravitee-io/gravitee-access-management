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
package io.gravitee.am.management.service.telemetry.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One batch of the weekly domain pass, schema version 1, as accepted by
 * {@code POST /v1/am/domains}. The {@code runId} groups the batches of one pass and
 * {@code batch} counts from 1. The batch carrying {@code last} closes the run.
 *
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainBatch(
    int schemaVersion,
    String product,
    String installationId,
    String label,
    String runId,
    int batch,
    boolean last,
    String sentAt,
    List<DomainRecord> domains
) {
    public static final int SCHEMA_VERSION = 1;
    public static final String PRODUCT = "am";
}
