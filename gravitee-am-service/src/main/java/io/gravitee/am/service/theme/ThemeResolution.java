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
package io.gravitee.am.service.theme;

import io.gravitee.am.model.Theme;
import org.springframework.util.ObjectUtils;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThemeResolution {
    private static final String THEME_BACKGROUND_COLOR_CONTEXT_KEY = "--primary-background-color";
    private static final String THEME_TEXT_COLOR_CONTEXT_KEY = "--primary-foreground-color";
    private static final String THEME_LIGHT_BACKGROUND_COLOR_CONTEXT_KEY = "--secondary-background-color";
    private static final String THEME_LIGHT_TEXT_COLOR_CONTEXT_KEY = "--secondary-foreground-color";
    private static final String THEME_LOGO_WIDTH_CONTEXT_KEY = "--logo-width";

    private String faviconUrl;
    private String logoUrl;
    private int logoWidth;
    private String css;
    private String customCss;

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public int getLogoWidth() {
        return logoWidth;
    }

    public void setLogoWidth(int logoWidth) {
        this.logoWidth = logoWidth;
    }

    public String getCss() {
        return css;
    }

    public void setCss(String css) {
        this.css = css;
    }

    public String getCustomCss() {
        return customCss;
    }

    public void setCustomCss(String customCss) {
        this.customCss = customCss;
    }

    public static ThemeResolution build(Theme theme) {
        ThemeResolution themeResolution = new ThemeResolution();
        themeResolution.setFaviconUrl(theme.getFaviconUrl());
        themeResolution.setLogoUrl(theme.getLogoUrl());
        themeResolution.setLogoWidth(theme.getLogoWidth());
        themeResolution.setCss(buildCss(theme));
        themeResolution.setCustomCss(theme.getCss());
        return themeResolution;
    }

    private static String buildCss(Theme theme) {
        StringBuilder sb = new StringBuilder();
        sb.append(":root {");
        buildCss(sb, THEME_BACKGROUND_COLOR_CONTEXT_KEY, theme.getPrimaryButtonColorHex());
        buildCss(sb, THEME_TEXT_COLOR_CONTEXT_KEY, theme.getPrimaryTextColorHex());
        buildCss(sb, THEME_LIGHT_BACKGROUND_COLOR_CONTEXT_KEY, theme.getSecondaryButtonColorHex());
        buildCss(sb, THEME_LIGHT_TEXT_COLOR_CONTEXT_KEY, theme.getSecondaryTextColorHex());
        if (theme.getLogoWidth() > 0) {
            buildCss(sb, THEME_LOGO_WIDTH_CONTEXT_KEY, theme.getLogoWidth() + "px");
        }
        sb.append("}");
        return sb.toString();
    }

    private static void buildCss(StringBuilder sb, String key, Object value) {
        if (!ObjectUtils.isEmpty(value)) {
            sb.append(key);
            sb.append(":");
            sb.append(value);
            sb.append(";");
        }
    }
}
