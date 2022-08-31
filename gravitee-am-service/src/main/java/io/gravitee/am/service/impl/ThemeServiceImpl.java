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
package io.gravitee.am.service.impl;

import com.google.common.base.Strings;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Theme;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.ThemeRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.ThemeService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.model.NewTheme;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ThemeAuditBuilder;
import io.gravitee.am.service.validators.theme.ThemeValidator;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Objects;

import static io.gravitee.am.common.event.Type.THEME;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ThemeServiceImpl implements ThemeService {

    private final Logger logger = LoggerFactory.getLogger(ThemeServiceImpl.class);

    @Lazy
    @Autowired
    private ThemeRepository themeRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ThemeValidator themeValidator;

    @Override
    public Maybe<Theme> findByReference(ReferenceType referenceType, String referenceId) {
        return this.themeRepository.findByReference(referenceType, referenceId)
                .onErrorResumeNext(ex -> {
                    logger.error("An error occurs while trying to find the theme linked to {}/{}", referenceType, referenceId, ex);
                    return Maybe.error(new TechnicalManagementException("An error occurs while trying to find the theme", ex));
                });
    }

    @Override
    public Single<Theme> create(Domain domain, NewTheme newTheme, User principal) {
        Objects.requireNonNull(domain, "domain is required to create a theme");
        Objects.requireNonNull(newTheme, "newTheme is required to create a theme");
        Theme theme = new Theme();

        theme.setReferenceType(ReferenceType.DOMAIN);
        theme.setReferenceId(domain.getId());

        theme.setCss(newTheme.getCss());
        theme.setLogoUrl(newTheme.getLogoUrl());
        theme.setLogoWidth(newTheme.getLogoWidth());
        theme.setFaviconUrl(newTheme.getFaviconUrl());
        theme.setPrimaryTextColorHex(newTheme.getPrimaryTextColorHex());
        theme.setPrimaryButtonColorHex(newTheme.getPrimaryButtonColorHex());
        theme.setSecondaryButtonColorHex(newTheme.getSecondaryButtonColorHex());
        theme.setSecondaryTextColorHex(newTheme.getSecondaryTextColorHex());

        final Date now = new Date();
        theme.setCreatedAt(now);
        theme.setUpdatedAt(now);

        // currently we only support one theme per domain
        return themeValidator.validate(theme)
                .andThen(
                        themeRepository.findByReference(ReferenceType.DOMAIN, domain.getId())
                                .isEmpty()
                                .flatMap(isEmpty -> {
                                    if (!isEmpty) {
                                        return Single.error(new ThemeAlreadyExistsException());
                                    }
                                    return this.themeRepository.create(sanitize(theme))
                                            .flatMap(createdTheme -> {
                                                Event event = new Event(THEME, new Payload(createdTheme.getId(), createdTheme.getReferenceType(), createdTheme.getReferenceId(), Action.CREATE));
                                                return eventService
                                                        .create(event)
                                                        .flatMap(createdEvent -> Single.just(createdTheme));
                                            })
                                            .onErrorResumeNext(ex -> {
                                                String msg = "An error occurred while trying to create a theme";
                                                logger.error(msg, ex);
                                                return Single.error(new TechnicalManagementException(msg, ex));
                                            })
                                            .doOnSuccess(dictionary -> auditService.report(AuditBuilder
                                                    .builder(ThemeAuditBuilder.class)
                                                    .principal(principal)
                                                    .type(EventType.THEME_CREATED)
                                                    .theme(theme)))
                                            .doOnError(throwable -> auditService.report(AuditBuilder
                                                    .builder(ThemeAuditBuilder.class)
                                                    .principal(principal)
                                                    .type(EventType.THEME_CREATED)
                                                    .theme(theme)
                                                    .throwable(throwable)));
                                }));
    }

    @Override
    public Single<Theme> update(Domain domain, Theme updatedTheme, User principal) {
        Objects.requireNonNull(domain, "domain is required to update a theme");
        Objects.requireNonNull(updatedTheme, "updatedTheme is required to update a theme");
        Objects.requireNonNull(updatedTheme.getId(), "theme id is required to update a theme");

        return this.themeRepository.findById(updatedTheme.getId())
                .switchIfEmpty(Maybe.error(new ThemeNotFoundException(updatedTheme.getId(), domain.getId())))
                .flatMapSingle(existingTheme -> {

                    if (!domain.getId().equals(updatedTheme.getReferenceId())) {
                        return Single.error(new InvalidThemeException("Updated theme is not linked to the domain"));
                    }

                    if (!existingTheme.getReferenceId().equals(updatedTheme.getReferenceId())) {
                        return Single.error(new InvalidThemeException("ReferenceId can not be updated"));
                    }

                    if (!existingTheme.getReferenceType().equals(updatedTheme.getReferenceType())) {
                        return Single.error(new InvalidThemeException("ReferenceType can not be updated"));
                    }

                    final Date now = new Date();
                    updatedTheme.setUpdatedAt(now);
                    updatedTheme.setCreatedAt(existingTheme.getCreatedAt());

                    return themeValidator.validate(updatedTheme)
                            .andThen(this.themeRepository.update(sanitize(updatedTheme))
                            .flatMap(newTheme -> {
                                Event event = new Event(THEME, new Payload(newTheme.getId(), newTheme.getReferenceType(), newTheme.getReferenceId(), Action.UPDATE));
                                return eventService
                                        .create(event)
                                        .flatMap(createdEvent -> Single.just(newTheme));
                            })
                            .onErrorResumeNext(ex -> {
                                String msg = "An error occurred while trying to update a theme";
                                logger.error(msg, ex);
                                return Single.error(new TechnicalManagementException(msg, ex));
                            })
                            .doOnSuccess(dictionary -> auditService.report(AuditBuilder
                                    .builder(ThemeAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.THEME_UPDATED)
                                    .theme(updatedTheme)
                                    .oldValue(existingTheme)
                            ))
                            .doOnError(throwable -> auditService.report(AuditBuilder
                                    .builder(ThemeAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.THEME_UPDATED)
                                    .theme(updatedTheme)
                                    .oldValue(existingTheme)
                                    .throwable(throwable))));
                });
    }

    @Override
    public Completable delete(Domain domain, String themeId, User principal) {
        return this.themeRepository.findById(themeId).flatMapCompletable(theme -> {
            if (!(ReferenceType.DOMAIN.equals(theme.getReferenceType()) && domain.getId().equals(theme.getReferenceId()))) {
                logger.warn("Delete theme '{}' received on wrong domain, delete skipped");
                return Completable.error(new InvalidThemeException("Theme isn't linked to the domain " + domain.getId()));
            }

            return this.themeRepository.delete(themeId)
                    .doOnComplete(() -> {
                        Event event = new Event(THEME, new Payload(theme.getId(), theme.getReferenceType(), theme.getReferenceId(), Action.DELETE));
                        eventService.create(event).ignoreElement().subscribe();
                        auditService.report(AuditBuilder
                                .builder(ThemeAuditBuilder.class)
                                .principal(principal)
                                .type(EventType.THEME_DELETED)
                                .theme(null)
                                .oldValue(theme));
                    })
                    .onErrorResumeNext(ex -> {
                        String msg = "An error occurred while trying to delete a theme";
                        logger.error(msg, ex);
                        auditService.report(AuditBuilder
                                .builder(ThemeAuditBuilder.class)
                                .principal(principal)
                                .type(EventType.THEME_DELETED)
                                .theme(theme)
                                .throwable(ex));
                        return Completable.error(new TechnicalManagementException(msg, ex));
                    });
        });
    }

    @Override
    public Maybe<Theme> getTheme(Domain domain, String themeId) {
        return this.themeRepository.findById(themeId).flatMap(theme -> {
            if (!(ReferenceType.DOMAIN.equals(theme.getReferenceType()) && domain.getId().equals(theme.getReferenceId()))) {
                return Maybe.error(new ThemeNotFoundException(themeId, domain.getId()));
            }
            return Maybe.just(theme);
        });
    }

    @Override
    public Completable validate(Theme theme) {
        return this.themeValidator.validate(theme);
    }

    public Theme sanitize(Theme theme) {
        var safeTheme = new Theme(theme);
        if (!Strings.isNullOrEmpty(safeTheme.getFaviconUrl())) {
            safeTheme.setFaviconUrl(StringEscapeUtils.escapeEcmaScript(safeTheme.getFaviconUrl()));
        }
        if (!Strings.isNullOrEmpty(safeTheme.getLogoUrl())) {
            safeTheme.setLogoUrl(StringEscapeUtils.escapeEcmaScript(safeTheme.getLogoUrl()));
        }
        return theme;
    }
}
