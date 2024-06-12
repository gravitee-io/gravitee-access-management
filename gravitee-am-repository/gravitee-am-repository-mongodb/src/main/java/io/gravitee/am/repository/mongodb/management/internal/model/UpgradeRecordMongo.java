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

import io.gravitee.node.api.upgrader.UpgradeRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpgradeRecordMongo {
    @BsonId
    private String id;
    private Date appliedAt;

    public static UpgradeRecordMongo from(UpgradeRecord value) {
        return new UpgradeRecordMongo(value.getId(), value.getAppliedAt());
    }

    public UpgradeRecord from() {
        return new UpgradeRecord(getId(), getAppliedAt());
    }
}
