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
package io.gravitee.am.repository.jdbc.management.api.model;

import io.gravitee.node.api.upgrader.UpgradeRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("upgraders")
public class JdbcUpgradeRecord implements Persistable<String> {
    @Id
    private String id;
    @Column("applied_at")
    private LocalDateTime appliedAt;

    // the default insert vs update strategy assumes that new entities have un-set ids,
    // causing an update when we want an insert
    @Transient
    boolean isNew;

    public static JdbcUpgradeRecord newFrom(UpgradeRecord value) {
        var appliedAt = value.getAppliedAt().toInstant().atOffset(ZoneOffset.UTC);
        return new JdbcUpgradeRecord(value.getId(), LocalDateTime.from(appliedAt), true);
    }

    public UpgradeRecord toDomain() {
        return new UpgradeRecord(getId(), Date.from(getAppliedAt().atOffset(ZoneOffset.UTC).toInstant()));
    }
}
