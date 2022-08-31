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
package io.gravitee.am.management.handlers.management.api.preview;

import com.google.common.base.Strings;
import io.gravitee.am.management.handlers.management.api.authentication.view.TemplateResolver;
import io.gravitee.am.management.handlers.management.api.model.PreviewRequest;
import io.gravitee.am.management.handlers.management.api.model.PreviewResponse;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.I18nDictionary;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.Theme;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ThemeService;
import io.gravitee.am.service.i18n.DynamicDictionaryProvider;
import io.gravitee.am.service.i18n.FileSystemDictionaryProvider;
import io.gravitee.am.service.i18n.GraviteeMessageResolver;
import io.gravitee.am.service.i18n.SpringGraviteeMessageSource;
import io.gravitee.am.service.i18n.ThreadLocalDomainDictionaryProvider;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.theme.ThemeResolution;
import io.reactivex.Maybe;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring5.SpringTemplateEngine;

import java.util.Locale;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class PreviewServiceImpl implements PreviewService, InitializingBean {

    @Autowired
    private DomainService domainService;

    @Autowired
    private ThemeService themeService;

    @Autowired
    private I18nDictionaryService i18nDictionaryService;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private TemplateResolver templateResolver;

    @Value("${templates.path:#{systemProperties['gravitee.home']}/templates}")
    private String templatesDirectory;

    private GraviteeMessageResolver graviteeMessageResolver;

    private final DynamicDictionaryProvider domainBasedDictionaryProvider = new ThreadLocalDomainDictionaryProvider();

    @Override
    public void afterPropertiesSet() throws Exception {
        final FileSystemDictionaryProvider fileSystemDictionaryProvider = new FileSystemDictionaryProvider(templatesDirectory.endsWith("/") ? templatesDirectory + "i18n/" : templatesDirectory + "/i18n/");
        if (this.templateEngine instanceof SpringTemplateEngine) {
            this.graviteeMessageResolver = new SpringGraviteeMessageSource(fileSystemDictionaryProvider, domainBasedDictionaryProvider);
            ((SpringTemplateEngine) this.templateEngine).setMessageSource((MessageSource) this.graviteeMessageResolver);
        } else {
            this.graviteeMessageResolver = new GraviteeMessageResolver(fileSystemDictionaryProvider, domainBasedDictionaryProvider);
            this.templateEngine.addMessageResolver(this.graviteeMessageResolver);
        }
    }

    public Maybe<PreviewResponse> previewDomainForm(String domainId, PreviewRequest previewRequest) {
        if (!isNullOrEmpty(previewRequest.getTemplate())) {
            try {
                Template.parse(previewRequest.getTemplate());
            } catch (IllegalArgumentException e) {
                return Maybe.error(new PreviewException("Invalid template ['" + previewRequest.getTemplate() +"']"));
            }
        }

        return this.themeService.validate(previewRequest.getTheme())
                .andThen(domainService.findById(domainId)
                        .map(domain -> new PreviewBuilder(templateEngine, templateResolver)
                                .withDomain(domain)
                                .withRequest(previewRequest))
                        .flatMap(builder -> loadTheme(domainId, previewRequest.getTheme()).map(theme -> builder.withTheme(theme)))
                        .flatMap(builder -> loadDefaultDictionary(builder.getDomain()).map(locale -> builder.withLocale(locale)))
                        .map(PreviewBuilder::buildPreview));
    }

    private Maybe<ThemeResolution> loadTheme(String domainId, Theme overrideTheme) {
        return this.themeService.findByReference(ReferenceType.DOMAIN, domainId)
                .switchIfEmpty(Maybe.fromCallable(() -> {
                    final Theme defaultTheme = new Theme();
                    defaultTheme.setPrimaryTextColorHex("#000000");
                    defaultTheme.setPrimaryButtonColorHex("#6A4FF7");
                    defaultTheme.setSecondaryTextColorHex("#000000");
                    defaultTheme.setSecondaryButtonColorHex("#DAD3FD");
                    return defaultTheme;
                }))
                .map(theme -> merge(theme, overrideTheme))
                .map(themeService::sanitize)
                .map(ThemeResolution::build);
    }

    private static Theme merge(Theme theme, Theme overrideTheme) {
        if (overrideTheme != null) {
            if (!isNullOrEmpty(overrideTheme.getFaviconUrl())) {
                theme.setFaviconUrl(overrideTheme.getFaviconUrl());
            }
            if (!isNullOrEmpty(overrideTheme.getLogoUrl())) {
                theme.setLogoUrl(overrideTheme.getLogoUrl());
            }
            if (overrideTheme.getLogoWidth() > 0) {
                theme.setLogoWidth(overrideTheme.getLogoWidth());
            }
            if (!isNullOrEmpty(overrideTheme.getCss())) {
                theme.setCss(overrideTheme.getCss());
            }
            if (!isNullOrEmpty(overrideTheme.getPrimaryButtonColorHex())) {
                theme.setPrimaryButtonColorHex(overrideTheme.getPrimaryButtonColorHex());
            }
            if (!isNullOrEmpty(overrideTheme.getPrimaryTextColorHex())) {
                theme.setPrimaryTextColorHex(overrideTheme.getPrimaryTextColorHex());
            }
            if (!isNullOrEmpty(overrideTheme.getSecondaryButtonColorHex())) {
                theme.setSecondaryButtonColorHex(overrideTheme.getSecondaryButtonColorHex());
            }
            if (!isNullOrEmpty(overrideTheme.getSecondaryTextColorHex())) {
                theme.setSecondaryTextColorHex(overrideTheme.getSecondaryTextColorHex());
            }
        }
        return theme;
    }

    private Maybe<Locale> loadDefaultDictionary(Domain domain) {
        // TODO load default locale using domain settings when available (GH#8067)
        return this.i18nDictionaryService.findAll(ReferenceType.DOMAIN, domain.getId())
                .firstElement()
                .map(Optional::ofNullable)
                .switchIfEmpty(Maybe.just(Optional.empty()))
                .map(optDictionary ->
                        optDictionary
                            .map(dict -> {
                                this.graviteeMessageResolver.updateDictionary(dict);
                                return dict;
                            })
                            .map(I18nDictionary::getLocale)
                            .map(Locale::new)
                            .orElse(Locale.ENGLISH));
    }
}
