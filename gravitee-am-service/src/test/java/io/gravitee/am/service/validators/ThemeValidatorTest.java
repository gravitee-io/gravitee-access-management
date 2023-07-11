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

import io.gravitee.am.model.Theme;
import io.gravitee.am.service.exception.ThemeInvalidException;
import io.gravitee.am.service.validators.theme.ThemeValidator;
import io.gravitee.am.service.validators.theme.impl.ThemeValidatorImpl;
import io.reactivex.rxjava3.observers.TestObserver;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThemeValidatorTest {

    private ThemeValidator themeValidator;

    @BeforeEach
    public void before(){
        themeValidator = new ThemeValidatorImpl();
    }

    @Test
    public void shouldValidate_NewTheme() {
        final TestObserver<Void> observer = themeValidator.validate(new Theme()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
    }

    @Test
    public void shouldValidate_Favicon() {
        final Theme theme = new Theme();
        theme.setFaviconUrl("http://comewhere/image.png");
        final TestObserver<Void> observer = themeValidator.validate(theme).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
    }

    @Test
    public void shouldValidate_Logo() {
        final Theme theme = new Theme();
        theme.setLogoUrl("http://comewhere/image.png");
        final TestObserver<Void> observer = themeValidator.validate(theme).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
    }

    @Test
    public void shouldNotValidate_Logo() {
        final Theme theme = new Theme();
        theme.setLogoUrl("file://comewhere/image.png");
        final TestObserver<Void> observer = themeValidator.validate(theme).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(ThemeInvalidException.class);
    }

}
