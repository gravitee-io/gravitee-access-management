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
package io.gravitee.am.service.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.DataSource;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DataSourceService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.model.NewEnvironment;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.EnvironmentAuditBuilder;
import io.gravitee.common.util.EnvironmentUtils;
import io.gravitee.common.util.ObservableSet;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class DataSourceServiceImpl implements DataSourceService {

    private static final String DATASOURCES_PREFIX = "datasources.";

//    @Autowired
//    StandardEnvironment environment;

    @Override
    public Flowable<DataSource> findAll() {
        log.debug("Find all datasources");

        // Hardcoded for now...
        Set<DataSource> items = Set.of(new DataSource("id-1", "name-1", "description"), new DataSource("id-2", "name-2", "desc2"));

        return Flowable.fromIterable(items);
    }

//    public Observable<DataSource> getDatasourceIdentifierKeys() {
//        return Observable.fromCallable(() -> EnvironmentUtils
//                        .getPropertiesStartingWith(environment, DATASOURCES_PREFIX))
//                .flatMapIterable(Map::keySet)
//                .map(ds ->  {
//                    DataSource dataSource = new DataSource();
//                    return ataSourcedataSource;
//                }
//                .filter(key -> key.contains(".id"));
//    }
}