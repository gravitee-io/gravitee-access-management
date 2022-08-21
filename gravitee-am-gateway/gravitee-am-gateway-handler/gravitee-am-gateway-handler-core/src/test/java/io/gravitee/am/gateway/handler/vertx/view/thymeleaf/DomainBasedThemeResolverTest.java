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
package io.gravitee.am.gateway.handler.vertx.view.thymeleaf;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Theme;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DomainBasedThemeResolverTest {

    @Mock
    private Domain domain;

    @InjectMocks
    private DomainBasedThemeResolver domainBasedThemeResolver = new DomainBasedThemeResolver();

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn("domain-id");
    }

    @Test
    public void shouldResolveTheme_no_custom_theme() {
        Map<String, Object> context = new HashMap<>();
        domainBasedThemeResolver.resolveTheme(context);

        ThemeResolution themeResolution = (ThemeResolution) context.get("theme");
        Assert.assertTrue(themeResolution != null);
        Assert.assertTrue(isEmpty(themeResolution));
    }

    @Test
    public void shouldResolveTheme_custom_theme() {
        Theme customTheme = buildTheme();

        Map<String, Object> context = new HashMap<>();
        domainBasedThemeResolver.updateTheme(customTheme);
        domainBasedThemeResolver.resolveTheme(context);

        ThemeResolution themeResolution = (ThemeResolution) context.get("theme");
        Assert.assertTrue(themeResolution != null);
        Assert.assertTrue(customTheme.getFaviconUrl().equals(themeResolution.getFaviconUrl()));
        Assert.assertTrue(customTheme.getLogoUrl().equals(themeResolution.getLogoUrl()));
        Assert.assertTrue(customTheme.getLogoWidth() == (themeResolution.getLogoWidth()));
        Assert.assertTrue(customTheme.getCss().equals(themeResolution.getCustomCss()));
        Assert.assertTrue(themeResolution.getCss().equals(":root {--primary-background-color:#ffffff;--primary-foreground-color:#000000;--secondary-background-color:#fafafa;--secondary-foreground-color:#000000;--logo-width:250px;}"));
    }

    @Test
    public void shouldRemoveTheme() {
        Theme customTheme = buildTheme();

        Map<String, Object> context = new HashMap<>();
        domainBasedThemeResolver.updateTheme(customTheme);
        domainBasedThemeResolver.removeTheme(customTheme.getReferenceId());
        domainBasedThemeResolver.resolveTheme(context);

        ThemeResolution themeResolution = (ThemeResolution) context.get("theme");
        Assert.assertTrue(themeResolution != null);
        Assert.assertTrue(isEmpty(themeResolution));
    }

    private static Theme buildTheme() {
        Theme customTheme = new Theme();
        customTheme.setReferenceType(ReferenceType.DOMAIN);
        customTheme.setReferenceId("domain-id");
        customTheme.setCss("a { text-decoration: none; }");
        customTheme.setPrimaryButtonColorHex("#ffffff");
        customTheme.setPrimaryTextColorHex("#000000");
        customTheme.setSecondaryButtonColorHex("#fafafa");
        customTheme.setSecondaryTextColorHex("#000000");
        customTheme.setFaviconUrl("https://favicon.ico");
        customTheme.setLogoUrl("https://logo.png");
        customTheme.setLogoWidth(250);

        return customTheme;
    }

    private static boolean isEmpty(ThemeResolution themeResolution) {
        return Stream.of(
                themeResolution.getCss(),
                themeResolution.getCustomCss(),
                themeResolution.getFaviconUrl(),
                themeResolution.getLogoUrl()).allMatch(Objects::isNull);
    }
}
