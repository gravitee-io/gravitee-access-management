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

import io.gravitee.am.repository.jdbc.management.api.model.JdbcPasswordHistory;
import io.reactivex.Flowable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava2CrudRepository;

public interface SpringPasswordHistoryRepository extends RxJava2CrudRepository<JdbcPasswordHistory, String> {
    @Query("SELECT * FROM password_histories ph WHERE ph.reference_type = :refType AND ph.reference_id = :refId AND ph.user_id = :userId")
    Flowable<JdbcPasswordHistory> findByUserId(@Param("refType") String referenceType, @Param("refId") String referenceId, @Param("userId") String userId);
}
