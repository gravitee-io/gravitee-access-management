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

import io.gravitee.am.model.Domain;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.MultiMap;
import jakarta.annotation.Nullable;

import java.util.List;

public interface DomainReadService {
    Maybe<Domain> findById(String id);
    /**
     * @param requestOrigin the {@code scheme://host[:port]} the end user reached the gateway on, or null
     *                      or blank for flows with no end-user request (SCIM, management API). Honoured
     *                      only in managed cloud, and only when it matches one of the environment's
     *                      entrypoints; anything else falls back to the configured resolution.
     */
    String buildUrl(Domain domain, String path, MultiMap queryParams, @Nullable String requestOrigin);

    default String buildUrl(Domain domain, String path, MultiMap queryParams) {
        return buildUrl(domain, path, queryParams, null);
    }

    default String buildUrl(Domain domain, String path) {
        return buildUrl(domain, path, MultiMap.caseInsensitiveMultiMap());
    }

    Flowable<Domain> listAll();
}
