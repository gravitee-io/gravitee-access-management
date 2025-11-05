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
package io.gravitee.am.service.validators.url;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class UrlValidatorTest {

    private UrlValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new UrlValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @Nested
    @DisplayName("Valid URLs")
    class ValidUrls {

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
                "http://example.com",
                "https://example.com",
                "HTTPS://EXAMPLE.COM",
                "http://example.c",
                "http://localhost",
                "http://user@localhost",
                "https://sub.domain.org/path?x=1",
                "ftp://files.server.net:21/data",
                "git+ssh://github.com/user/repo.git",
                "s3://my-bucket.s3.amazonaws.com/file.txt",
                "http://example.co.uk",
                "custom+proto://a-b.c-d123.io/some/path"
        })
        void shouldAcceptValidUrls(String url) {
            assertTrue(validator.isValid(url, null), "URL should be valid " + url);
        }
    }

    @Nested
    @DisplayName("Wrong URLs")
    class InvalidUrls {

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
                "example.com",
                "example",
                "www.example.com",
                "mailto:user@example.com",
                "://example.com",
                "http//example.com",
                "https:/example.com",
                "https://",
                "https:// example .com",
                "text https://example.com text"
        })
        void shouldRejectInvalidUrls(String url) {
            assertFalse(validator.isValid(url, null), "URL should be invalid: " + url);
        }

    }

    @Test
    @DisplayName("Full match test")
    void fullMatchOnly() {
        String wrapped = "prefix https://example.com suffix";
        assertFalse(validator.isValid(wrapped, null));
        assertTrue(validator.isValid("https://example.com", null));
    }

    @Nested
    @DisplayName("Fragment validation")
    class FragmentValidation {

        @Test
        @DisplayName("Should accept URLs with fragments when allowFragment is true")
        void shouldAcceptFragmentsWhenAllowed() {
            validator.initialize(createUrlAnnotation(true));

            assertTrue(validator.isValid("http://example.com#fragment", context));
            assertTrue(validator.isValid("https://example.com/path#section", context));
        }

        @Test
        @DisplayName("Should reject URLs with fragments when allowFragment is false")
        void shouldRejectFragmentsWhenDisallowed() {
            validator.initialize(createUrlAnnotation(false));

            assertFalse(validator.isValid("http://example.com#fragment", context));
            assertFalse(validator.isValid("https://example.com/path#section", context));
            assertFalse(validator.isValid("http://example.com?query=1#frag", context));
            assertTrue(validator.isValid("http://example.com", context));
            assertTrue(validator.isValid("https://example.com/path", context));
            assertTrue(validator.isValid("http://example.com?query=1", context));
        }

        private Url createUrlAnnotation(boolean allowFragment) {
            return new Url() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return Url.class;
                }
        
                @Override
                public String message() {
                    return "Invalid URL";
                }
        
                @Override
                public Class<?>[] groups() {
                    return new Class[0];
                }
        
                @Override
                @SuppressWarnings("unchecked")
                public Class<? extends jakarta.validation.Payload>[] payload() {
                    return (Class<? extends jakarta.validation.Payload>[]) new Class[0];
                }
        
                @Override
                public boolean allowFragment() {
                    return allowFragment;
                }
            };
        }
    }

}