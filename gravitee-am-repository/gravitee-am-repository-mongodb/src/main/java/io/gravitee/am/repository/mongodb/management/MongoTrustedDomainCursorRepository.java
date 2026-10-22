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
package io.gravitee.am.repository.mongodb.management;

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.cursor.CursorRequest;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.gravitee.am.model.oidc.TrustedDomainCursorRequest;
import io.gravitee.am.model.oidc.TrustedDomainUsage;
import io.gravitee.am.repository.management.api.TrustedDomainCursorRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.TrustedDomainMongo;
import io.reactivex.rxjava3.core.Single;
import jakarta.annotation.PostConstruct;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.ne;
import static io.gravitee.am.repository.mongodb.management.MongoTrustedDomainRepository.COLLECTION_NAME;
import static io.gravitee.am.repository.mongodb.management.MongoTrustedDomainRepository.FIELD_DOMAIN_IDENTIFIER;
import static io.gravitee.am.repository.mongodb.management.MongoTrustedDomainRepository.FIELD_SPIFFE_TRUST_DOMAIN;
import static io.gravitee.am.repository.mongodb.management.MongoTrustedDomainRepository.FIELD_XAA_ENABLED;

@Component
public class MongoTrustedDomainCursorRepository
        extends AbstractMongoCursorRepository<TrustedDomainMongo, TrustedDomain, TrustedDomainCursorRequest>
        implements TrustedDomainCursorRepository {

    private static final String SORT_FIELD_NAME = "name";
    private static final String FIELD_TOKEN_EXCHANGE_ENABLED = "tokenExchange.enabled";

    @Autowired
    private MongoTrustedDomainRepository trustedDomainRepository;

    private MongoCollection<TrustedDomainMongo> trustedDomainsCollection;

    @PostConstruct
    public void init() {
        trustedDomainsCollection = mongoOperations.getCollection(COLLECTION_NAME, TrustedDomainMongo.class);
    }

    @Override
    public Single<CursorPage<TrustedDomain, TrustedDomainCursorRequest>> findByReferenceCursor(ReferenceType referenceType, String referenceId, TrustedDomainCursorRequest cursor, int limit) {
        Bson filter = withUsage(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)), cursor.getUsage());
        return findCursorPage(trustedDomainsCollection, filter, cursor, limit);
    }

    @Override
    protected TrustedDomain toModel(TrustedDomainMongo databaseModel) {
        return trustedDomainRepository.toEntity(databaseModel);
    }

    @Override
    protected Bson withQuery(Bson base, String query) {
        return base;
    }

    @Override
    protected String sortFieldName(CursorRequest cursor) {
        String sortField = cursor.getSortField() != null ? cursor.getSortField() : SORT_FIELD_NAME;
        if (!SORT_FIELD_NAME.equals(sortField)) {
            throw new IllegalArgumentException("Invalid sort field: " + sortField);
        }
        return FIELD_NAME;
    }

    @Override
    protected Object sortFieldValue(CursorRequest cursor) {
        return cursor.getLastSortValue();
    }

    @Override
    protected boolean useCollation(TrustedDomainCursorRequest cursor) {
        return true;
    }

    @Override
    protected TrustedDomainCursorRequest nextCursor(TrustedDomainCursorRequest currentCursor, TrustedDomain element) {
        return new TrustedDomainCursorRequest(
                element.getName(),
                element.getId(),
                currentCursor.getSortDirection(),
                currentCursor.getSortField(),
                currentCursor.getPage() + 1,
                currentCursor.getUsage());
    }

    private static Bson withUsage(Bson filter, TrustedDomainUsage usage) {
        if (usage == null) {
            return filter;
        }
        return switch (usage) {
            case SPIFFE -> and(filter, ne(FIELD_SPIFFE_TRUST_DOMAIN, null));
            case TOKEN_EXCHANGE -> and(filter, eq(FIELD_TOKEN_EXCHANGE_ENABLED, true), ne(FIELD_DOMAIN_IDENTIFIER, null));
            case CROSS_APP_ACCESS -> and(filter, eq(FIELD_XAA_ENABLED, true));
        };
    }
}
