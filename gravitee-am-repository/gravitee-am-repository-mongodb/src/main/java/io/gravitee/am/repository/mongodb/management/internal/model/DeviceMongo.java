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

package io.gravitee.am.repository.mongodb.management.internal.model;

import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.util.Date;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
@Accessors(chain = true)
public class DeviceMongo {
    // TODO [DP] class to remove
    @BsonId
    private String id;

    private String referenceType;
    private String referenceId;

    private String client;
    private String userId;
    private String deviceIdentifierId;
    private String deviceId;

    private String type;

    @BsonProperty("created_at")
    private Date createdAt;
    @BsonProperty("expires_at")
    private Date expiresAt;

}
