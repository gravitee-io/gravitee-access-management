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
package io.gravitee.am.repository.ratelimit;

import static java.lang.Class.forName;
import static org.springframework.util.StringUtils.capitalize;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.repository.RepositoriesTestInitializer;
import io.gravitee.am.repository.exceptions.TechnicalException;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.gravitee.am.repository.oauth2.test.config.OAuthTestConfigurationLoader;
import io.gravitee.am.repository.ratelimit.test.config.RateLimitTestConfigurationLoader;
import jakarta.inject.Inject;
import org.apache.commons.io.FilenameUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

/**
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {RateLimitTestConfigurationLoader.class},
        loader = AnnotationConfigContextLoader.class)
public abstract class AbstractRateLimitTest {

    private static final String JSON_EXTENSION = "json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired
    private RepositoriesTestInitializer testRepositoryInitializer;

    protected abstract String getTestCasesPath();

    protected abstract String getModelPackage();

    protected abstract void createModel(Object object) throws TechnicalException;

    @Before
    public void setUp() throws Exception {
        if (testRepositoryInitializer != null) {
            testRepositoryInitializer.before(this.getClass());
        }

        if (getTestCasesPath() != null) {
            URL testCaseResource = AbstractRateLimitTest.class.getResource(getTestCasesPath());

            if (testCaseResource == null) {
                throw new IllegalStateException("No resource defined in " + getTestCasesPath());
            }

            final File directory = new File(testCaseResource.toURI());

            final File[] files = directory.listFiles(pathname ->
                    pathname.isFile() && JSON_EXTENSION.equalsIgnoreCase(FilenameUtils.getExtension(pathname.toString()))
            );

            for (final File file : getSortedFilesList(files)) {
                createModels(file);
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        if (testRepositoryInitializer != null) {
            testRepositoryInitializer.after(this.getClass());
        }
    }

    protected Class<?> getClassFromFileName(final String baseName) {
        final String className = capitalize(baseName.substring(0, baseName.length() - 1));
        try {
            return forName(getModelPackage() + className);
        } catch (final ClassNotFoundException e) {
            throw new IllegalArgumentException("The entity cannot be found for " + className, e);
        }
    }

    protected <T> List<T> mapToModel(final File file, final Class<T> clazz) throws Exception {
        return MAPPER.readValue(file, MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
    }

    private List<File> getSortedFilesList(File[] files) {
        return Stream.of(files).sorted((o1, o2) -> o2.getName().compareTo(o1.getName())).collect(Collectors.toList());
    }

    private void createModels(File file) throws Exception {
        final Class<?> c = getClassFromFileName(FilenameUtils.getBaseName(file.getName()));

        for (final Object object : mapToModel(file, c)) {
            createModel(object);
        }
    }

    @Configuration
    @ComponentScan(
            value = "io.gravitee.am.repository",
            includeFilters = {
                @ComponentScan.Filter(pattern = ".*TestRepository.*", type = FilterType.REGEX),
                @ComponentScan.Filter(pattern = ".*RepositoriesTestInitializer", type = FilterType.REGEX)
            },
            useDefaultFilters = false
    )
    static class ContextConfiguration {}
}