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

import io.gravitee.am.service.exception.InvalidPathException;
import io.gravitee.am.service.validators.path.PathValidator;
import io.gravitee.am.service.validators.path.PathValidatorImpl;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PathValidatorTest {

    private PathValidator pathValidator;

    @Before
    public void before(){
        pathValidator = new PathValidatorImpl();
    }

    @Test
    public void validate() {
        pathValidator.validate("/test").test().assertNoErrors();
    }

    @Test
    public void validateSpecialCharacters() {
        pathValidator.validate("/test/subpath/subpath2_with-and.dot/AND_UPPERCASE").test().assertNoErrors();
    }

    @Test
    public void validate_invalidEmptyPath() {
        pathValidator.validate("").test().assertError(InvalidPathException.class);
    }

    @Test
    public void validate_nullPath() {
        pathValidator.validate(null).test().assertError(InvalidPathException.class);
    }

    @Test
    public void validate_multipleSlashesPath() {
        pathValidator.validate("/////test////").test().assertError(InvalidPathException.class);
    }

    @Test
    public void validate_invalidCharacters() {
        pathValidator.validate("/test$:\\;,+").test().assertError(InvalidPathException.class);
    }
}