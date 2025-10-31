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
package io.gravitee.am.repository.jdbc.provider.common;

import io.gravitee.node.api.upgrader.UpgradeRecord;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

abstract public class AbstractJdbcUpgraderRepository implements UpgraderRepository {

    protected abstract String getTableName();

    protected abstract DatabaseClient getDatabaseClient();

    @Override
    public Maybe<UpgradeRecord> findById(String id) {
        var publisher = getDatabaseClient().sql("SELECT id, applied_at FROM " + getTableName() + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> new UpgradeRecord(
                        row.get("id", String.class),
                        toDate(row.get("applied_at", LocalDateTime.class))))
                .first();
        return Maybe.fromPublisher(publisher)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<UpgradeRecord> create(UpgradeRecord upgradeRecord) {
        var publisher = getDatabaseClient().sql("INSERT INTO " + getTableName() + " (id, applied_at) VALUES (:id, :appliedAt)")
                .bind("id", upgradeRecord.getId())
                .bind("appliedAt", toLocalDateTime(upgradeRecord.getAppliedAt()))
                .fetch()
                .rowsUpdated()
                .flatMap(rowsInserted -> rowsInserted == 1 ?
                        Mono.just(upgradeRecord) :
                        Mono.error(new RuntimeException("Number of rows inserted should be 1 but is " + rowsInserted)));
        return Single.fromPublisher(publisher)
                .observeOn(Schedulers.computation());
    }

    public LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        var appliedAt = date.toInstant().atOffset(ZoneOffset.UTC);
        return LocalDateTime.from(appliedAt);
    }

    private Date toDate(LocalDateTime datetime) {
        if (datetime == null) {
            return null;
        }
        return Date.from(datetime.atOffset(ZoneOffset.UTC).toInstant());
    }
}
