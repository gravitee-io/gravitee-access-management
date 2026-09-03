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

import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.util.Date;
import java.util.Objects;
import java.util.Set;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class DeviceAuthorizationRequestMongo {

    @BsonId
    private String id;

    @BsonProperty("user_code")
    private String userCode;

    private String status;

    private String subject;

    private Set<String> scopes;

    @BsonProperty("client")
    private String client;

    @BsonProperty("created_at")
    private Date createdAt;

    @BsonProperty("last_access_at")
    private Date lastAccessAt;

    @BsonProperty("expire_at")
    private Date expireAt;

    @BsonProperty("interval_increment")
    private int intervalIncrement;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(id, ((DeviceAuthorizationRequestMongo) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
