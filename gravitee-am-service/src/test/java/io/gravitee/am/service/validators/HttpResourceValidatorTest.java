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


import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.validators.resource.ResourceValidator;
import io.gravitee.am.service.validators.resource.http.HttpResourceValidator;
import io.gravitee.am.service.validators.resource.http.HttpResourceValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class HttpResourceValidatorTest {
    private HttpResourceValidator httpResourceValidator;

    @BeforeEach
    void setup() {
        httpResourceValidator = new HttpResourceValidatorImpl(new ObjectMapper());
    }

    @Test
    void must_not_validate_invalid() {
        assertTrue(httpResourceValidator.validate(new ResourceValidator.ResourceHolder("other-policy", "{}")).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("params_that_must_validate_regarding_configuration")
    void must_validate_invalid_regarding_configuration(String configuration, boolean expected) {
        var resourceHolder = new ResourceValidator.ResourceHolder(HttpResourceValidatorImpl.HTTP_AM_RESOURCE, configuration);
        assertEquals(expected, httpResourceValidator.validate(resourceHolder).isEmpty());
    }

    public static Stream<Arguments> params_that_must_validate_regarding_configuration() {
        return Stream.of(
                Arguments.of("{\"auth\": { \"type\": \"none\" }}", true),
                Arguments.of("{\"auth\": { \"type\": \"oauth2\" }}", false),
                Arguments.of("{\"auth\": { \"type\": \"oauth2\", \"oauth2\": {\"endpoint\": \"https://token\", \"clientId\": \"clId\"} }}", false),
                Arguments.of("{\"auth\": { \"type\": \"oauth2\", \"oauth2\": {\"endpoint\": \"https://token\", \"clientSecret\": \"secr\"} }}", false),
                Arguments.of("{\"auth\": { \"type\": \"oauth2\", \"oauth2\": {\"clientId\": \"clId\", \"clientSecret\": \"secr\"} }}", false),
                Arguments.of("{\"auth\": { \"type\": \"oauth2\", \"oauth2\": {\"endpoint\": \"https://token\", \"clientId\": \"clId\", \"clientSecret\": \"secr\"} }}", true)

        );
    }
}
