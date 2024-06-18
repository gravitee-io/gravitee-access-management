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
package io.gravitee.am.reporter.kafka.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Copied from File reporter and renamed to match Kafka Semantics
 * @author Florent Amaridon
 * @author Visiativ
 */
@Setter
@Getter
public class AuditMessageValueDto {

    private String id;
    private String transactionId;
    private String type;
    private String referenceType;
    private String referenceId;
    private AuditAccessPointDto accessPoint;
    private AuditEntityDto actor;
    private AuditEntityDto target;
    private String status;
    private Instant timestamp;
    private String environmentId;
    private String organizationId;
    private String domainId;
    private String nodeId;
    private String nodeHostname;
}
