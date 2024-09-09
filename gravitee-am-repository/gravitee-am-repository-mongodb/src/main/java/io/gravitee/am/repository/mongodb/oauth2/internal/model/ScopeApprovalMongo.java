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
package io.gravitee.am.repository.mongodb.oauth2.internal.model;

import io.gravitee.am.repository.mongodb.common.model.Auditable;
import lombok.Data;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Date;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
public class ScopeApprovalMongo extends Auditable {

    @BsonId
    private String id;

    private String transactionId;

    private String userId;
    private String userExternalId;
    private String userSource;

    private String clientId;

    private String scope;

    private String status;

    private Date expiresAt;

    /**
     * Security domain associated to the scope
     */
    private String domain;

}
