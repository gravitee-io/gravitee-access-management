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
package io.gravitee.am.dataplane.mongodb.repository.model;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.mongodb.common.model.Auditable;
import lombok.Getter;
import lombok.Setter;
import org.bson.Document;

import java.util.Date;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class CertificateCredentialMongo extends Auditable {

    private String id;
    private ReferenceType referenceType;
    private String referenceId;
    private String userId;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String deviceName;

    // Certificate-specific fields
    private String certificatePem;
    private String certificateThumbprint;
    private String certificateSubjectDN;
    private String certificateSerialNumber;
    private Date certificateExpiresAt;

    // Optional/extensible fields
    private Document metadata;

    private Date accessedAt;
}

