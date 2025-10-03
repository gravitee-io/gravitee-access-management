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

import io.gravitee.am.model.DataSource;
import io.gravitee.am.service.spring.datasource.DataSourcesConfiguration;
import io.gravitee.am.service.DataSourceService;
import io.reactivex.rxjava3.core.Flowable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author GraviteeSource Team
 */
@Component
public class DataSourceServiceImpl implements DataSourceService {

    @Autowired
    private DataSourcesConfiguration configuration;

    @Override
    public Flowable<DataSource> findAll() {
        return Flowable.fromIterable(configuration.getDataSourcesAsSet());
    }
}