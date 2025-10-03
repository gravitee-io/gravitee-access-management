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

package io.gravitee.am.service.validators.idp;

import com.nimbusds.jose.util.JSONObjectUtils;
import io.gravitee.am.service.exception.InvalidDataSourceException;
import io.gravitee.am.service.spring.datasource.DataSourcesConfiguration;
import io.gravitee.common.util.EnvironmentUtils;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Map;
import java.util.Objects;

@Component
public class DatasourceValidatorImpl implements DatasourceValidator {
    private static final Logger logger = LoggerFactory.getLogger(DatasourceValidatorImpl.class);

    public static final String DATASOURCE_ID_KEY = "datasourceId";

    @Autowired
    private DataSourcesConfiguration dataSourcesConfiguration;

    public Completable validate(String configuration) {
        return Observable.fromCallable(() -> configuration)
                .filter(Objects::nonNull)
                .map(this::extractDatasourceId)
                .filter(datasourceId -> !datasourceId.isEmpty())
                .flatMapCompletable(this::validateDatasourceId)
                .onErrorResumeNext(throwable -> {
                    if (throwable instanceof ParseException) {
                        logger.warn("Unable to parse configuration for identity provider", throwable);
                        return Completable.complete();
                    }
                    return Completable.error(throwable);
                });
    }

    private String extractDatasourceId(String configuration) throws ParseException {
        Map<String, Object> cfg = JSONObjectUtils.parse(configuration);
        return (String) cfg.getOrDefault(DATASOURCE_ID_KEY, "");
    }

    private Completable validateDatasourceId(String datasourceId) {
        logger.debug("validating datasource ID: {}", datasourceId);
        if (dataSourcesConfiguration.getDataSourceKeyById(datasourceId) == null) {
            throw new InvalidDataSourceException("Could not find datasource with id: " + datasourceId);
        }
        return Completable.complete();
    }
}
