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
import io.gravitee.am.service.validators.idp.DatasourceValidator;
import io.gravitee.am.service.validators.idp.DatasourceValidatorImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;
import java.util.Properties;

import static io.gravitee.am.service.validators.idp.DatasourceValidatorImpl.DATASOURCE_ID_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class DatasourceValidatorTest {

    private static final String MONGODB_DATASOURCE_PATTERN = "datasources.mongodb[%d].id";

    @Mock
    private StandardEnvironment mockEnv;

    private DatasourceValidator validator;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new DatasourceValidatorImpl(mockEnv);
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
        // Setup mock environment with datasource properties
        setupMockEnvironmentWithDatasources("test", "other-datasource");
        
        Completable validate = validator.validate(String.format("{\"%s\": \"test\"}", DATASOURCE_ID_KEY));
        validate.test().assertNoErrors();
    }

    @Test
    public void shouldThrowErrorWhenDatasourceNotFound() {
        // Setup mock environment with different datasource
        setupMockEnvironmentWithDatasources("other-datasource");
        
        Completable validate = validator.validate(String.format("{\"%s\": \"test\"}", DATASOURCE_ID_KEY));
        validate.test().assertError(InvalidDataSourceException.class);
    }

    @Test
    public void shouldNotThrowErrorWhenInvalidJson() {
        Completable validate = validator.validate("invalid json");
        validate.test().assertNoErrors(); // Should log warning and complete
    }

    @Test
    public void shouldHandleMultipleDatasources() {
        // Setup mock environment with multiple datasources
        setupMockEnvironmentWithDatasources("mongodb-datasource", "postgres-datasource", "redis-datasource");
        
        // Test finding the first one
        Completable validate1 = validator.validate(String.format("{\"%s\": \"mongodb-datasource\"}", DATASOURCE_ID_KEY));
        validate1.test().assertNoErrors();
        
        // Test finding the second one
        Completable validate2 = validator.validate(String.format("{\"%s\": \"postgres-datasource\"}", DATASOURCE_ID_KEY));
        validate2.test().assertNoErrors();
        
        // Test finding the third one
        Completable validate3 = validator.validate(String.format("{\"%s\": \"redis-datasource\"}", DATASOURCE_ID_KEY));
        validate3.test().assertNoErrors();
    }

    @Test
    public void shouldReturnEmptyObservableWhenNoDatasourceKeys() {
        // Setup mock environment with no datasource properties
        setupMockEnvironmentWithDatasources();
        
        Observable<String> keysObservable = validator.getDatasourceIdentifierKeys();
        TestObserver<String> testObserver = keysObservable.test();
        
        testObserver.assertComplete()
                   .assertNoValues()
                   .assertNoErrors();
    }

    @Test
    public void shouldReturnSingleDatasourceKey() {
        // Setup mock environment with one datasource
        setupMockEnvironmentWithDatasources("test-datasource");
        
        Observable<String> keysObservable = validator.getDatasourceIdentifierKeys();
        TestObserver<String> testObserver = keysObservable.test();
        
        testObserver.assertComplete()
                   .assertValueCount(1)
                   .assertValue(String.format(MONGODB_DATASOURCE_PATTERN, 0))
                   .assertNoErrors();
    }

    @Test
    public void shouldReturnMultipleDatasourceKeys() {
        // Setup mock environment with multiple datasources
        setupMockEnvironmentWithDatasources("mongodb-datasource", "postgres-datasource", "redis-datasource");
        
        Observable<String> keysObservable = validator.getDatasourceIdentifierKeys();
        TestObserver<String> testObserver = keysObservable.test();
        
        testObserver.assertComplete()
                   .assertValueCount(3)
                   .assertNoErrors();
        
        // Check that all expected keys are present (order may vary)
        List<String> values = testObserver.values();
        assertTrue(values.contains(String.format(MONGODB_DATASOURCE_PATTERN, 0)));
        assertTrue(values.contains(String.format(MONGODB_DATASOURCE_PATTERN, 1)));
        assertTrue(values.contains(String.format(MONGODB_DATASOURCE_PATTERN, 2)));
    }

    @Test
    public void shouldFilterOnlyKeysContainingId() {
        // Setup mock environment with mixed properties (some with .id, some without)
        MutablePropertySources propertySources = new MutablePropertySources();
        Properties properties = new Properties();
        
        // Add properties with .id (should be included)
        properties.setProperty(String.format(MONGODB_DATASOURCE_PATTERN, 0), "mongodb-datasource");
        properties.setProperty("datasources.postgres[0].id", "postgres-datasource");
        
        // Add properties without .id (should be filtered out)
        properties.setProperty("datasources.mongodb[0].url", "mongodb://localhost:27017");
        properties.setProperty("datasources.postgres[0].url", "jdbc:postgresql://localhost:5432");
        properties.setProperty("datasources.redis[0].host", "localhost");
        
        PropertiesPropertySource propertySource = new PropertiesPropertySource("test", properties);
        propertySources.addFirst(propertySource);
        
        when(mockEnv.getPropertySources()).thenReturn(propertySources);
        
        // Mock individual property access
        when(mockEnv.getProperty(String.format(MONGODB_DATASOURCE_PATTERN, 0), String.class)).thenReturn("mongodb-datasource");
        when(mockEnv.getProperty("datasources.postgres[0].id", String.class)).thenReturn("postgres-datasource");
        
        Observable<String> keysObservable = validator.getDatasourceIdentifierKeys();
        TestObserver<String> testObserver = keysObservable.test();
        
        testObserver.assertComplete()
                   .assertValueCount(2)
                   .assertNoErrors();
        
        // Check that only the .id keys are present (order may vary)
        List<String> values = testObserver.values();
        assertTrue(values.contains(String.format(MONGODB_DATASOURCE_PATTERN, 0)));
        assertTrue(values.contains("datasources.postgres[0].id"));
    }

    @Test
    public void shouldHandleReactiveStreamingCorrectly() {
        // Setup mock environment with multiple datasources
        setupMockEnvironmentWithDatasources("ds1", "ds2", "ds3", "ds4", "ds5");
        
        Observable<String> keysObservable = validator.getDatasourceIdentifierKeys();
        
        // Test that it's a proper reactive stream
        List<String> collectedKeys = keysObservable
                .toList()
                .blockingGet();
        
        assertEquals(5, collectedKeys.size());
        
        // Check that all expected keys are present (order may vary)
        assertTrue(collectedKeys.contains(String.format(MONGODB_DATASOURCE_PATTERN, 0)));
        assertTrue(collectedKeys.contains(String.format(MONGODB_DATASOURCE_PATTERN, 1)));
        assertTrue(collectedKeys.contains(String.format(MONGODB_DATASOURCE_PATTERN, 2)));
        assertTrue(collectedKeys.contains(String.format(MONGODB_DATASOURCE_PATTERN, 3)));
        assertTrue(collectedKeys.contains(String.format(MONGODB_DATASOURCE_PATTERN, 4)));
    }

    private void setupMockEnvironmentWithDatasources(String... datasourceIds) {
        MutablePropertySources propertySources = new MutablePropertySources();
        Properties properties = new Properties();
        
        // Add datasource properties
        for (int i = 0; i < datasourceIds.length; i++) {
            String key = String.format(MONGODB_DATASOURCE_PATTERN, i);
            properties.setProperty(key, datasourceIds[i]);
        }
        
        PropertiesPropertySource propertySource = new PropertiesPropertySource("test", properties);
        propertySources.addFirst(propertySource);
        
        when(mockEnv.getPropertySources()).thenReturn(propertySources);
        
        // Mock individual property access for the validation logic
        for (int i = 0; i < datasourceIds.length; i++) {
            String key = String.format(MONGODB_DATASOURCE_PATTERN, i);
            when(mockEnv.getProperty(key, String.class)).thenReturn(datasourceIds[i]);
        }
    }
}
