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
import io.gravitee.am.model.Theme;
import io.gravitee.am.service.theme.ThemeResolution;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainBasedThemeResolver {

    private static final String THEME_CONTEXT_KEY = "theme";

    private final ConcurrentMap<String, ThemeResolution> themes = new ConcurrentHashMap<>();
    private final ThemeResolution defaultThemeResolution = new ThemeResolution();

    @Autowired
    private Domain domain;

    public void resolveTheme(Map<String, Object> context) {
        ThemeResolution themeResolution = themes.getOrDefault(domain.getId(), defaultThemeResolution);
        context.put(THEME_CONTEXT_KEY, themeResolution);
    }

    public void updateTheme(Theme theme) {
        if (theme != null) {
            ThemeResolution themeResolution = ThemeResolution.build(theme);
            themes.put(theme.getReferenceId(), themeResolution);
        }
    }

    public void removeTheme(String referenceId) {
        themes.remove(referenceId);
    }

}
