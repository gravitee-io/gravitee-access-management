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
package io.gravitee.am.service.validators;

import io.gravitee.am.service.exception.InvalidDataSourceException;
import io.gravitee.am.service.spring.datasource.DataSourcesConfiguration;
import io.gravitee.am.service.validators.idp.DatasourceValidatorImpl;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static io.gravitee.am.service.validators.idp.DatasourceValidatorImpl.DATASOURCE_ID_KEY;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DatasourceValidatorTest {

    @Mock
    private DataSourcesConfiguration dataSourcesConfiguration;

    private DatasourceValidatorImpl validator;

    @BeforeEach
    public void setUp() {
        validator = new DatasourceValidatorImpl();
        ReflectionTestUtils.setField(validator, "dataSourcesConfiguration", dataSourcesConfiguration);
    }

    @Test
    public void shouldNotThrowErrorWhenDatasourceIsMissing() {
        Completable validate = validator.validate("{}");
        validate.test().assertNoErrors();
    }

    @Test
    public void shouldNotThrowErrorWhenDatasourceIsEmpty() {
        Completable validate = validator.validate(String.format("{\"%s\": \"\"}", DATASOURCE_ID_KEY));
        validate.test().assertNoErrors();
    }

    @Test
    public void shouldNotThrowErrorWhenDatasourceIsPresent() {
        when(dataSourcesConfiguration.getDataSourceKeyById("test")).thenReturn("datasources.mongodb[0]");
        
        Completable validate = validator.validate(String.format("{\"%s\": \"test\"}", DATASOURCE_ID_KEY));
        validate.test().assertNoErrors();
    }

    @Test
    public void shouldThrowErrorWhenDatasourceNotFound() {
        when(dataSourcesConfiguration.getDataSourceKeyById("test")).thenReturn(null);
        
        Completable validate = validator.validate(String.format("{\"%s\": \"test\"}", DATASOURCE_ID_KEY));
        validate.test().assertError(InvalidDataSourceException.class);
    }

    @Test
    public void shouldNotThrowErrorWhenInvalidJson() {
        Completable validate = validator.validate("invalid json");
        validate.test().assertNoErrors();
    }

    @Test
    public void shouldHandleMultipleDatasources() {
        when(dataSourcesConfiguration.getDataSourceKeyById("mongodb-datasource")).thenReturn("datasources.mongodb[0]");
        when(dataSourcesConfiguration.getDataSourceKeyById("postgres-datasource")).thenReturn("datasources.postgres[0]");
        when(dataSourcesConfiguration.getDataSourceKeyById("redis-datasource")).thenReturn("datasources.redis[0]");

        Completable validate1 = validator.validate(String.format("{\"%s\": \"mongodb-datasource\"}", DATASOURCE_ID_KEY));
        validate1.test().assertNoErrors();

        Completable validate2 = validator.validate(String.format("{\"%s\": \"postgres-datasource\"}", DATASOURCE_ID_KEY));
        validate2.test().assertNoErrors();

        Completable validate3 = validator.validate(String.format("{\"%s\": \"redis-datasource\"}", DATASOURCE_ID_KEY));
        validate3.test().assertNoErrors();
    }
}