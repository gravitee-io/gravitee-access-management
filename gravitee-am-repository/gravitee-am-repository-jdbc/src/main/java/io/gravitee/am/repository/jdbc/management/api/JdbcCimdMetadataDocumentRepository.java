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
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcCimdMetadataDocument;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringCimdMetadataDocumentRepository;
import io.gravitee.am.repository.management.api.CimdMetadataDocumentRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author GraviteeSource Team
 */
@Repository
public class JdbcCimdMetadataDocumentRepository extends AbstractJdbcRepository implements CimdMetadataDocumentRepository {

    private static final String COL_ID = "id";
    private static final String COL_DOMAIN_ID = "domain_id";
    private static final String COL_CLIENT_ID = "client_id";
    private static final String COL_EXPIRES_AT = "expires_at";

    @Autowired
    private SpringCimdMetadataDocumentRepository springRepo;

    @Override
    public Maybe<CimdMetadataDocument> findById(String id) {
        LOGGER.debug("findById({})", id);
        return springRepo.findById(id).map(this::toEntity).observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<CimdMetadataDocument> findByDomainAndClientId(String domainId, String clientId) {
        LOGGER.debug("findByDomainAndClientId({}, {})", domainId, clientId);
        return springRepo.findByDomainAndClientId(domainId, clientId)
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<CimdMetadataDocument> findByDomain(String domainId) {
        LOGGER.debug("findByDomain({})", domainId);
        return fluxToFlowable(getTemplate().select(JdbcCimdMetadataDocument.class)
                .matching(Query.query(where(COL_DOMAIN_ID).is(domainId))).all())
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CimdMetadataDocument> create(CimdMetadataDocument item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create CimdMetadataDocument with id {}", item.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CimdMetadataDocument> update(CimdMetadataDocument item) {
        LOGGER.debug("update CimdMetadataDocument with id {}", item.getId());
        return monoToSingle(getTemplate().update(toJdbcEntity(item))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(getTemplate().delete(JdbcCimdMetadataDocument.class)
                .matching(Query.query(where(COL_ID).is(id))).all())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainAndClientId(String domainId, String clientId) {
        LOGGER.debug("deleteByDomainAndClientId({}, {})", domainId, clientId);
        return monoToCompletable(getTemplate().delete(JdbcCimdMetadataDocument.class)
                .matching(Query.query(where(COL_DOMAIN_ID).is(domainId).and(where(COL_CLIENT_ID).is(clientId)))).all())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        LocalDateTime now = LocalDateTime.now(UTC);
        return monoToCompletable(getTemplate().delete(JdbcCimdMetadataDocument.class)
                .matching(Query.query(where(COL_EXPIRES_AT).lessThan(now))).all())
                .doOnError(error -> LOGGER.error("Unable to purge CimdMetadataDocuments", error))
                .observeOn(Schedulers.computation());
    }

    private CimdMetadataDocument toEntity(JdbcCimdMetadataDocument jdbc) {
        if (jdbc == null) return null;
        CimdMetadataDocument doc = new CimdMetadataDocument();
        doc.setId(jdbc.getId());
        doc.setDomainId(jdbc.getDomainId());
        doc.setClientId(jdbc.getClientId());
        doc.setMetadata(jdbc.getMetadata());
        doc.setFetchedAt(toDate(jdbc.getFetchedAt()));
        doc.setExpiresAt(toDate(jdbc.getExpiresAt()));
        doc.setUpdatedAt(toDate(jdbc.getUpdatedAt()));
        return doc;
    }

    private JdbcCimdMetadataDocument toJdbcEntity(CimdMetadataDocument doc) {
        if (doc == null) return null;
        JdbcCimdMetadataDocument jdbc = new JdbcCimdMetadataDocument();
        jdbc.setId(doc.getId());
        jdbc.setDomainId(doc.getDomainId());
        jdbc.setClientId(doc.getClientId());
        jdbc.setMetadata(doc.getMetadata());
        jdbc.setFetchedAt(toLocalDateTime(doc.getFetchedAt()));
        jdbc.setExpiresAt(toLocalDateTime(doc.getExpiresAt()));
        jdbc.setUpdatedAt(toLocalDateTime(doc.getUpdatedAt()));
        return jdbc;
    }

}
