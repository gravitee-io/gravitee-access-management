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
package io.gravitee.am.management.handlers.management.api.authentication.provider.generator;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static io.gravitee.am.management.handlers.management.api.authentication.provider.generator.RedirectCookieGenerator.DEFAULT_UI_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author GraviteeSource Team
 */
public class RedirectCookieGeneratorTest {

    private RedirectCookieGenerator newGenerator(MockEnvironment environment) {
        RedirectCookieGenerator generator = new RedirectCookieGenerator();
        setField(generator, "environment", environment);
        return generator;
    }

    @Test
    public void should_use_default_ui_url_when_property_unset() {
        RedirectCookieGenerator generator = newGenerator(new MockEnvironment());

        assertEquals(DEFAULT_UI_URL, generator.getConsoleUiUrl());
        assertEquals("/auth/authorize?redirect_uri=http://localhost:4200/login/callback", generator.getDefaultRedirectUrl());
    }

    @Test
    public void should_use_default_ui_url_when_property_blank() {
        RedirectCookieGenerator generator = newGenerator(new MockEnvironment().withProperty("console.ui.url", ""));

        assertEquals(DEFAULT_UI_URL, generator.getConsoleUiUrl());
        assertEquals("/auth/authorize?redirect_uri=http://localhost:4200/login/callback", generator.getDefaultRedirectUrl());
    }

    @Test
    public void should_use_configured_console_ui_url() {
        RedirectCookieGenerator generator = newGenerator(new MockEnvironment().withProperty("console.ui.url", "https://am.example.com"));

        assertEquals("https://am.example.com", generator.getConsoleUiUrl());
        assertEquals("/auth/authorize?redirect_uri=https://am.example.com/login/callback", generator.getDefaultRedirectUrl());
    }

    @Test
    public void should_strip_single_trailing_slash() {
        RedirectCookieGenerator generator = newGenerator(new MockEnvironment().withProperty("console.ui.url", "https://am.example.com/"));

        assertEquals("https://am.example.com", generator.getConsoleUiUrl());
        assertEquals("/auth/authorize?redirect_uri=https://am.example.com/login/callback", generator.getDefaultRedirectUrl());
    }
}
