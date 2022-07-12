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
package io.gravitee.am.repository.jdbc.management.api.spring;

import io.gravitee.am.repository.jdbc.management.api.model.JdbcI18nDictionary;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava2CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public interface SpringI18nDictionaryRepository extends RxJava2CrudRepository<JdbcI18nDictionary, String> {

    @Query("select * from i18n_dictionaries f where f.reference_id = :refId and f.reference_type = :refType")
    Flowable<JdbcI18nDictionary> findAll(@Param("refType") String referenceType, @Param("refId") String referenceId);

    @Query("select * from i18n_dictionaries f where f.reference_type = :refType")
    Flowable<JdbcI18nDictionary> findAll(@Param("refType") String referenceType);

    @Query("select * from i18n_dictionaries f where f.reference_id = :refId and f.reference_type = :refType and f.id = :id")
    Maybe<JdbcI18nDictionary> findById(@Param("refType") String referenceType, @Param("refId") String referenceId, @Param("id") String id);

    @Query("select * from i18n_dictionaries f where f.reference_id = :refId and f.reference_type = :refType and f.name = :name")
    Maybe<JdbcI18nDictionary> findByName(@Param("refType") String referenceType, @Param("refId") String referenceId, @Param("name") String name);
}
