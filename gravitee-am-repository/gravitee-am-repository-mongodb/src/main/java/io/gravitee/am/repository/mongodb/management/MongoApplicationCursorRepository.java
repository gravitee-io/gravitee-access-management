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

import com.mongodb.BasicDBObject;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationCursorRequest;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.cursor.CursorRequest;
import io.gravitee.am.repository.management.api.ApplicationCursorRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationMongo;
import io.micrometer.common.util.StringUtils;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Function;
import jakarta.annotation.PostConstruct;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.repository.mongodb.management.MongoApplicationRepository.FIELD_CLIENT_ID;

@Component
public class MongoApplicationCursorRepository extends AbstractMongoCursorRepository<ApplicationMongo, Application, ApplicationCursorRequest> implements ApplicationCursorRepository {
    private MongoCollection<ApplicationMongo> applicationsCollection;

    @PostConstruct
    public void init() {
        applicationsCollection = mongoOperations.getCollection("applications", ApplicationMongo.class);
    }

    @Override
    public Single<CursorPage<Application, ApplicationCursorRequest>> findByDomainCursor(String domain, ApplicationCursorRequest cursor, int limit) {
        Bson filter = cursorFilter(eq(FIELD_DOMAIN, domain), cursor);
        return appCursorQuery(filter, cursor, limit);
    }

    @Override
    public Single<CursorPage<Application, ApplicationCursorRequest>> findByDomainAndIdsCursor(String domain, List<String> applicationIds, ApplicationCursorRequest cursor, int limit) {
        Bson filter = cursorFilter(and(eq(FIELD_DOMAIN, domain), in(FIELD_ID, applicationIds)), cursor);
        return appCursorQuery(filter, cursor, limit);
    }

    @Override
    protected Application toModel(ApplicationMongo model) {
        return MongoApplicationRepository.convert(model);
    }

    @Override
    protected Bson withQuery(Bson base, String query) {
        if (isWildcardQuery(query)) {
            // Wildcard search: use regex (cannot leverage indexes efficiently)
            // First escape regex metacharacters (except *) to prevent PatternSyntaxException
            String escapedQuery = escapeRegexMetacharacters(query);
            String compactQuery = escapedQuery.replaceAll("\\*+", ".*");
            String regex = "^" + compactQuery;
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            return and(base, or(new BasicDBObject(FIELD_CLIENT_ID, pattern), new BasicDBObject(FIELD_NAME, pattern)));
        } else {
            return and(base, or(eq(FIELD_CLIENT_ID, query), eq(FIELD_NAME, query)));
        }
    }

    @Override
    protected String sortFieldName(CursorRequest cursor){
        String sortField = cursor.getSortField() != null ? cursor.getSortField() : "updatedAt";
        return switch (sortField) {
            case "name" -> FIELD_NAME;
            case "updatedAt" -> FIELD_UPDATED_AT;
            default -> throw new IllegalArgumentException("Invalid sort field: " + sortField);
        };
    }

    @Override
    protected Object sortFieldValue(CursorRequest cursor) {
        String sortFieldName = sortFieldName(cursor);
        return switch (sortFieldName){
            case FIELD_UPDATED_AT -> dateFromString(cursor.getLastSortValue());
            default -> cursor.getLastSortValue();
        };
    }

    @Override
    protected boolean useCollation(ApplicationCursorRequest cursor) {
        return StringUtils.isNotBlank(cursor.getQuery()) && !isWildcardQuery(cursor.getQuery());
    }

    @Override
    protected ApplicationCursorRequest nextCursor(ApplicationCursorRequest currentCursor, Application element) {
        String lastSortValue = switch (sortFieldName(currentCursor)){
            case FIELD_NAME -> element.getName();
            case FIELD_UPDATED_AT -> dateToString(element.getUpdatedAt());
            default -> throw new IllegalStateException();
        };
        return new ApplicationCursorRequest(
                lastSortValue,
                element.getId(),
                currentCursor.getSortDirection(),
                currentCursor.getSortField(),
                currentCursor.getPage() + 1,
                currentCursor.getQuery(),
                currentCursor.getEnabled(),
                currentCursor.getTypes());
    }

    private Bson cursorFilter(Bson baseFilter, ApplicationCursorRequest cursor) {
        Bson filter;
        if (StringUtils.isNotBlank(cursor.getQuery())) {
            filter = withQuery(baseFilter, cursor.getQuery());
        } else {
            filter = baseFilter;
        }
        return filter;
    }

    private Single<CursorPage<Application, ApplicationCursorRequest>> appCursorQuery(Bson baseFilter, ApplicationCursorRequest cursor, int limit) {
        return findCursorPage(applicationsCollection, baseFilter, cursor, limit);
    }

    private static String dateToString(java.util.Date date) {
        return date != null ? String.valueOf(date.getTime()) : "0";
    }

    private static Date dateFromString(String value) {
        try {
            return new java.util.Date(Long.parseLong(value));
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid cursor: sort value '" + value + "' is not a valid timestamp", e);
        }
    }

}
