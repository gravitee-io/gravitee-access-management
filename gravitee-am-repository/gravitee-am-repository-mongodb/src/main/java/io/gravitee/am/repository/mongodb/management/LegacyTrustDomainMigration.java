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

import com.mongodb.client.model.Aggregates;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.reactivex.rxjava3.core.Completable;
import lombok.CustomLog;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.exists;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;

@CustomLog
class LegacyTrustDomainMigration {

    private static final String LEGACY_COLLECTION_NAME = "trust_domains";

    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_KEY_MATERIAL = "keyMaterial";
    private static final String FIELD_SPIFFE = "spiffe";
    private static final String FIELD_CROSS_APP_ACCESS = "crossAppAccess";

    private static final String LEGACY_FIELD_ISSUER = "issuer";
    private static final String LEGACY_FIELD_BUNDLE_SOURCE = "bundleSource";
    private static final String LEGACY_FIELD_JWKS_URL = "jwksUrl";
    private static final String LEGACY_FIELD_REFRESH_INTERVAL = "refreshIntervalSeconds";
    private static final String LEGACY_FIELD_ALLOWED_ALGORITHMS = "allowedAlgorithms";
    private static final String LEGACY_FIELD_SPIFFE_TRUST_DOMAIN = "spiffeTrustDomain";
    private static final String LEGACY_BUNDLE_SOURCE_STATIC_JWKS = "STATIC_JWKS";

    private final MongoDatabase database;
    private final String targetCollectionName;

    LegacyTrustDomainMigration(MongoDatabase database, String targetCollectionName) {
        this.database = database;
        this.targetCollectionName = targetCollectionName;
    }

    Completable run() {
        List<Bson> pipeline = List.of(
                Aggregates.match(and(
                        exists(LEGACY_FIELD_ISSUER, false),
                        exists(FIELD_CROSS_APP_ACCESS, false))),
                new Document("$project", new Document()
                        .append(FIELD_REFERENCE_ID, 1)
                        .append(FIELD_REFERENCE_TYPE, 1)
                        .append(FIELD_NAME, 1)
                        .append(FIELD_DESCRIPTION, 1)
                        .append(FIELD_CREATED_AT, 1)
                        .append(FIELD_UPDATED_AT, 1)
                        .append(FIELD_KEY_MATERIAL, keyMaterial())
                        .append(FIELD_SPIFFE, spiffe())),
                new Document("$merge", new Document("into", targetCollectionName)
                        .append("on", FIELD_ID)
                        .append("whenMatched", "keepExisting")
                        .append("whenNotMatched", "insert")));

        return Completable.fromPublisher(database.getCollection(LEGACY_COLLECTION_NAME).aggregate(pipeline))
                .doOnError(error -> log.warn("Unable to migrate the {} collection into {}",
                        LEGACY_COLLECTION_NAME, targetCollectionName, error))
                .onErrorComplete();
    }

    private static Document keyMaterial() {
        Document derived = new Document("$switch", new Document("branches", List.of(
                new Document("case", new Document("$eq", List.of("$" + LEGACY_FIELD_BUNDLE_SOURCE, LEGACY_BUNDLE_SOURCE_STATIC_JWKS)))
                        .append("then", new Document("source", KeyMaterialSource.JWK_SET.name())
                                .append("refreshIntervalSeconds", "$" + LEGACY_FIELD_REFRESH_INTERVAL)),
                new Document("case", isOfType(LEGACY_FIELD_BUNDLE_SOURCE, "string"))
                        .append("then", new Document("source", KeyMaterialSource.JWKS_URL.name())
                                .append("jwksUrl", "$" + LEGACY_FIELD_JWKS_URL)
                                .append("refreshIntervalSeconds", "$" + LEGACY_FIELD_REFRESH_INTERVAL))))
                .append("default", "$$REMOVE"));

        return new Document("$cond", new Document()
                .append("if", isOfType(FIELD_KEY_MATERIAL, "object"))
                .append("then", "$" + FIELD_KEY_MATERIAL)
                .append("else", derived));
    }

    private static Document spiffe() {
        return new Document()
                .append("spiffeTrustDomain", new Document("$ifNull",
                        List.of("$" + LEGACY_FIELD_SPIFFE_TRUST_DOMAIN, "$" + FIELD_NAME)))
                .append("allowedAlgorithms", "$" + LEGACY_FIELD_ALLOWED_ALGORITHMS);
    }

    private static Document isOfType(String field, String type) {
        return new Document("$eq", List.of(new Document("$type", "$" + field), type));
    }
}
