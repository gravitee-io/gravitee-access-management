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
package io.gravitee.am.reporter.api.provider;

import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.Reportable;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.time.Instant;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Reporter<R extends Reportable, C extends ReportableCriteria> extends io.gravitee.reporter.api.Reporter {

    /**
     * Maximum number of records purged per batch, whatever the backend (MongoDB or RDBMS). This hard
     * cap bounds memory usage, lock footprint and the number of bind parameters per delete statement
     * (e.g. SQL Server limits a statement to 2100 parameters), so any larger configured value is clamped.
     */
    int PURGE_MAX_BATCH_SIZE = 2000;

    Single<Page<R>> search(ReferenceType referenceType, String referenceId, C criteria, int page, int size);

    Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId,C criteria, Type analyticsType);

    Maybe<R> findById(ReferenceType referenceType, String referenceId, String id);

    boolean canSearch();

    default Completable purgeExpiredData(){
        return Completable.complete();
    }

    /**
     * Purge expired data while respecting a deadline shared across the whole purge execution.
     * <p>
     * Implementations must stop processing once {@code deadline} is reached and resume on the next
     * execution, so that a single run cannot monopolize the backend indefinitely.
     *
     * @param deadline the instant after which the purge must stop until the next execution
     */
    default Completable purgeExpiredData(Instant deadline){
        // by default, fall back to the legacy behaviour so existing reporters remain compatible
        return purgeExpiredData();
    }
}
