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
package io.gravitee.am.service.validators.jsonstring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsonStringValidatorTest {

    @Test
    public void validatorTest() {
        JsonStringValidator validator = new JsonStringValidator(new ObjectMapper());
        assertFalse(validator.isValid("{{}", Mockito.mock()));
        assertFalse(validator.isValid("{\"a:}", Mockito.mock()));
        assertFalse(validator.isValid("{\"xx\":}", Mockito.mock()));

        assertTrue(validator.isValid("", Mockito.mock()));
        assertTrue(validator.isValid("{\"aa\":\"xxx\"}", Mockito.mock()));
        assertTrue(validator.isValid("{\"aa\":[]}", Mockito.mock()));
        assertTrue(validator.isValid("{\"aa\":[1,2,3]}", Mockito.mock()));
    }


}