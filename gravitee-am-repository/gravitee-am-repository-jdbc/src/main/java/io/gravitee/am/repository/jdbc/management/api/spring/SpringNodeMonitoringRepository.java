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

import io.gravitee.am.repository.jdbc.management.api.model.JdbcMonitoring;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava2CrudRepository;

import java.time.LocalDateTime;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface SpringNodeMonitoringRepository extends RxJava2CrudRepository<JdbcMonitoring, String> {

    @Query("select * from node_monitoring m where m.node_id = :nodeId and m.type = :type")
    Maybe<JdbcMonitoring> findByNodeIdAndType(@Param("nodeId") String nodeId, @Param("type") String type);

    @Query("select * from node_monitoring m where m.type = :type and m.updated_at >= :from and m.updated_at <= :to")
    Flowable<JdbcMonitoring> findByTypeAndTimeFrame(@Param("type") String type, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
