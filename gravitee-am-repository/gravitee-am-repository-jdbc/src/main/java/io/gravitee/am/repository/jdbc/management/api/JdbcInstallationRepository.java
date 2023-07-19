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
import io.gravitee.am.model.Installation;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcInstallation;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringInstallationRepository;
import io.gravitee.am.repository.management.api.InstallationRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class JdbcInstallationRepository extends AbstractJdbcRepository implements InstallationRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";
    public static final String COL_ADDITIONAL_INFORMATION = "additional_information";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_CREATED_AT,
            COL_UPDATED_AT,
            COL_ADDITIONAL_INFORMATION
    );

    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;

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

    protected JdbcInstallation toJdbcEntity(Installation installation) {
        JdbcInstallation mapped = mapper.map(installation, JdbcInstallation.class);
        return mapped;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("installations", columns);
        this.UPDATE_STATEMENT = createUpdateStatement("installations", columns, List.of(COL_ID));
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

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec, COL_ID, installation.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(installation.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(installation.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, COL_ADDITIONAL_INFORMATION, installation.getAdditionalInformation());

        return monoToCompletable(insertSpec.then())
                .andThen(Single.defer(() -> this.findById(installation.getId()).toSingle()));
    }

    @Override
    public Single<Installation> update(Installation installation) {
        LOGGER.debug("update installation with id {}", installation.getId());

        DatabaseClient.GenericExecuteSpec update = getTemplate().getDatabaseClient().sql(UPDATE_STATEMENT);

        update = addQuotedField(update, COL_ID, installation.getId(), String.class);
        update = addQuotedField(update, COL_CREATED_AT, dateConverter.convertTo(installation.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, COL_UPDATED_AT, dateConverter.convertTo(installation.getUpdatedAt(), null), LocalDateTime.class);
        update = databaseDialectHelper.addJsonField(update, COL_ADDITIONAL_INFORMATION, installation.getAdditionalInformation());

        return monoToCompletable(update.fetch().rowsUpdated())
                .andThen(Single.defer(() -> this.findById(installation.getId()).toSingle()));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.installationRepository.deleteById(id);
    }
}
