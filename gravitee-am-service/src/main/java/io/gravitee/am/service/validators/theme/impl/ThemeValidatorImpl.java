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
package io.gravitee.am.service.validators.theme.impl;

import com.google.common.base.Strings;
import io.gravitee.am.model.Theme;
import io.gravitee.am.service.exception.ThemeInvalidException;
import io.gravitee.am.service.validators.theme.ThemeValidator;
import io.reactivex.Completable;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ThemeValidatorImpl implements ThemeValidator {

    @Override
    public Completable validate(Theme theme) {
        Completable result = Completable.complete();
        if (theme != null) {
            result = result.andThen(evalateUrl(theme.getLogoUrl(), theme, "logoUrl"))
                    .andThen(evalateUrl(theme.getFaviconUrl(), theme, "faviconUrl"));
        }
        return result;
    }

    private static Completable evalateUrl(String url, Theme theme, String attr) {
        Completable result = Completable.complete();
        if (!Strings.isNullOrEmpty(url)) {
            try {
                final URL resource = new URL(url);
                if (!(resource.getProtocol().equals("http") || resource.getProtocol().equals("https"))) {
                    result = Completable.error(new ThemeInvalidException(theme.getId(), attr));
                }
            } catch (MalformedURLException e) {
                result = Completable.error(new ThemeInvalidException(theme.getId(), attr));
            }
        }
        return result;
    }

}
