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

import io.gravitee.am.repository.jdbc.management.api.model.JdbcProtectedResource.JdbcProtectedResourceFeature;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.data.repository.reactive.RxJava3CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface SpringProtectedResourceFeatureRepository extends RxJava3CrudRepository<JdbcProtectedResourceFeature, String> {

    Flowable<JdbcProtectedResourceFeature> findAllByProtectedResourceId(String protectedResourceId);

    Flowable<JdbcProtectedResourceFeature> findAllByProtectedResourceIdIn(List<String> protectedResourceIds);

    Completable deleteByProtectedResourceId(String protectedResourceId);
}

