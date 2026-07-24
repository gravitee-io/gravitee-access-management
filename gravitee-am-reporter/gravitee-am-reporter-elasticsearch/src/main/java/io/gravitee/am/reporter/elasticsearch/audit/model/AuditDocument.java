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
package io.gravitee.am.reporter.elasticsearch.audit.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * Elasticsearch document representation of an {@link io.gravitee.am.reporter.api.audit.model.Audit}.
 * The field layout mirrors the MongoDB reporter so search/aggregation criteria address the same paths.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditDocument {

    private String id;
    private String transactionId;
    private String referenceType;
    private String referenceId;
    private String type;
    /** epoch millis (mapped as a date in the index template). */
    private long timestamp;

    private AuditEntityDocument actor;
    private AuditEntityDocument target;
    private AuditAccessPointDocument accessPoint;
    private AuditOutcomeDocument outcome;
}
