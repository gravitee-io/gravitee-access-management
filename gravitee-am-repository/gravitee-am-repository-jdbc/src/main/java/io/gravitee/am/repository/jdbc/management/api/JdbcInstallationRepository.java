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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Installation;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcIdentityProvider;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcInstallation;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringIdentityProviderRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringInstallationRepository;
import io.gravitee.am.repository.management.api.InstallationRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class JdbcInstallationRepository extends AbstractJdbcRepository implements InstallationRepository {

    @Autowired
    private SpringInstallationRepository installationRepository;

    protected Installation toEntity(JdbcInstallation installation) {
        Installation mapped = mapper.map(installation, Installation.class);
        // init to empty map t adopt same behaviour as Mongo Repository
        if (mapped.getAdditionalInformation() == null) {
            mapped.setAdditionalInformation(new HashMap<>());
        }
        return mapped;
    }

    @Override
    public Maybe<Installation> find() {
        LOGGER.debug("find()");
        return this.installationRepository.findAll().firstElement()
                .map(this::toEntity);
    }

    @Override
    public Maybe<Installation> findById(String id) {
        LOGGER.debug("findById({})", id);
        return this.installationRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Single<Installation> create(Installation installation) {
        installation.setId(installation.getId() == null ? RandomString.generate() : installation.getId());
        LOGGER.debug("create installation with id {}", installation.getId());

        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = dbClient.insert().into("installations");

        // doesn't use the class introspection to allow the usage of Json type in PostgreSQL
        insertSpec = addQuotedField(insertSpec, "id", installation.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, "created_at", dateConverter.convertTo(installation.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, "updated_at", dateConverter.convertTo(installation.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, "additional_information", installation.getAdditionalInformation());

        return monoToCompletable(insertSpec.then())
                .andThen(Single.defer(() -> this.findById(installation.getId()).toSingle()));
    }

    @Override
    public Single<Installation> update(Installation installation) {
        LOGGER.debug("update installation with id {}", installation.getId());

        DatabaseClient.GenericUpdateSpec updateSpec = dbClient.update().table("installations");
        Map<SqlIdentifier, Object> updateFields = new HashMap<>();

        // doesn't use the class introspection to allow the usage of Json type in PostgreSQL
        updateFields = addQuotedField(updateFields, "id", installation.getId(), String.class);
        updateFields = addQuotedField(updateFields, "created_at", dateConverter.convertTo(installation.getCreatedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields, "updated_at", dateConverter.convertTo(installation.getUpdatedAt(), null), LocalDateTime.class);
        updateFields = databaseDialectHelper.addJsonField(updateFields, "additional_information", installation.getAdditionalInformation());

        return monoToCompletable(updateSpec.using(Update.from(updateFields)).matching(from(where("id").is(installation.getId()))).then())
                .andThen(Single.defer(() -> this.findById(installation.getId()).toSingle()));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.installationRepository.deleteById(id);
    }
}
