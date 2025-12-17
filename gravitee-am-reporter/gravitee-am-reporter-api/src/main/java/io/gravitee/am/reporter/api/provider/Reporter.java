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

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface Reporter<R extends Reportable, C extends ReportableCriteria> extends io.gravitee.reporter.api.Reporter {

    Single<Page<R>> search(ReferenceType referenceType, String referenceId, C criteria, int page, int size);

    Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId,C criteria, Type analyticsType);

    Maybe<R> findById(ReferenceType referenceType, String referenceId, String id);

    boolean canSearch();

    default Completable purgeExpiredData(){
        return Completable.complete();
    }
}
