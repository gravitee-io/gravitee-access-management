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
package io.gravitee.am.service;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationCursorRequest;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.cursor.CursorRequest;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

public interface ApplicationSearcher {

    Single<CursorPage<Application, ApplicationCursorRequest>> searchByDomainCursor(String organization, String domain, ApplicationCursorRequest cursor, String ownerEmail,  int limit);

    Single<CursorPage<Application, ApplicationCursorRequest>> searchByDomainAndIdsCursor(String organization, String domain, List<String> applicationIds, ApplicationCursorRequest cursor, String ownerEmail, int limit);
}
