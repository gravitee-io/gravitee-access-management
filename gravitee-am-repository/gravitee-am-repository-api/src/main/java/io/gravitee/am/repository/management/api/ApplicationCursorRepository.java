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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationCursorRequest;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.cursor.CursorRequest;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.management.api.search.ApplicationCriteria;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface ApplicationCursorRepository {

    Single<CursorPage<Application, ApplicationCursorRequest>> findByDomainCursor(String domain, ApplicationCursorRequest cursor, int limit);

    Single<CursorPage<Application, ApplicationCursorRequest>> findByDomainAndIdsCursor(String domain, List<String> applicationIds, ApplicationCursorRequest cursor, int limit);

}
