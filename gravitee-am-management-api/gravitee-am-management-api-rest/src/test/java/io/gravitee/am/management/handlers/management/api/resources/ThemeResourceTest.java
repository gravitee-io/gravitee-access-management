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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.handlers.management.api.model.ThemeEntity;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Theme;
import io.gravitee.am.service.exception.ThemeNotFoundException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThemeResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-id-1";
    private static final String THEME_ID = "theme-id-1";

    @Before
    public void initialize() {
        reset(themeService);
    }

    @Test
    public void shouldNotGetTheme_UnknownDomain() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotGetTheme_UnknownTheme() {
        doReturn(Maybe.just(new Domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(themeService).getTheme(any(), eq(THEME_ID));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetTheme() {
        doReturn(Maybe.just(new Domain())).when(domainService).findById(DOMAIN_ID);

        final Theme theme = new Theme();
        theme.setId(THEME_ID);
        theme.setCss("css");
        theme.setFaviconUrl("favicon");
        theme.setLogoUrl("url");
        theme.setLogoWidth(222);
        theme.setReferenceId(DOMAIN_ID);
        theme.setReferenceType(ReferenceType.DOMAIN);
        theme.setPrimaryTextColorHex("#787787");
        theme.setPrimaryButtonColorHex("#877787");
        theme.setSecondaryButtonColorHex("#799955");
        final Date now = new Date();
        theme.setCreatedAt(now);
        theme.setUpdatedAt(now);
        doReturn(Maybe.just(theme)).when(themeService).getTheme(any(), eq(THEME_ID));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        assertSameEntity(theme, response);
    }

    @Test
    public void shouldNotUpdateTheme_UnknownDomain() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final ThemeEntity entity = new ThemeEntity();
        entity.setId(THEME_ID);
        entity.setReferenceId(DOMAIN_ID);
        entity.setReferenceType(ReferenceType.DOMAIN);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .put(Entity.json(entity));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotUpdateTheme_UnknownTheme() {
        doReturn(Maybe.just(new Domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.error(new ThemeNotFoundException(DOMAIN_ID, THEME_ID))).when(themeService).update(any(), any(), any());

        final ThemeEntity entity = new ThemeEntity();
        entity.setId(THEME_ID);
        entity.setReferenceId(DOMAIN_ID);
        entity.setReferenceType(ReferenceType.DOMAIN);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .put(Entity.json(entity));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotUpdateTheme_ThemeID_mismatch() {
        final ThemeEntity entity = new ThemeEntity();
        entity.setId("invalid");
        entity.setReferenceId(DOMAIN_ID);
        entity.setReferenceType(ReferenceType.DOMAIN);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .put(Entity.json(entity));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldNotUpdateTheme_DomainID_mismatch() {
        final ThemeEntity entity = new ThemeEntity();
        entity.setId(THEME_ID);
        entity.setReferenceId("invalid");
        entity.setReferenceType(ReferenceType.DOMAIN);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .put(Entity.json(entity));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldNotUpdateTheme_RefType_mismatch() {
        final ThemeEntity entity = new ThemeEntity();
        entity.setId(THEME_ID);
        entity.setReferenceId(DOMAIN_ID);
        entity.setReferenceType(ReferenceType.APPLICATION);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .put(Entity.json(entity));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldUpdateTheme_acceptMissingIds() {
        final ThemeEntity updateTheme = new ThemeEntity();
        updateTheme.setCss("cssUpdate");
        updateTheme.setFaviconUrl("favicon");

        final Theme theme = new Theme();
        theme.setId(THEME_ID);
        theme.setCss("css");
        theme.setFaviconUrl("favicon");
        theme.setLogoUrl("url");
        theme.setLogoWidth(222);
        theme.setReferenceId(DOMAIN_ID);
        theme.setReferenceType(ReferenceType.DOMAIN);
        theme.setPrimaryTextColorHex("#787787");
        theme.setPrimaryButtonColorHex("#877787");
        theme.setSecondaryButtonColorHex("#799955");
        final Date now = new Date();
        theme.setCreatedAt(now);
        theme.setUpdatedAt(now);

        doReturn(Maybe.just(new Domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(theme)).when(themeService).update(any(), any(), any());

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .put(Entity.json(updateTheme));

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        assertSameEntity(theme, response);

        verify(themeService).update(any(), argThat(input ->
                updateTheme.getCss().equals(input.getCss())
                && THEME_ID.equals(input.getId())
                && DOMAIN_ID.equals(input.getReferenceId())
                && ReferenceType.DOMAIN.equals(input.getReferenceType())), any());
    }

    @Test
    public void shouldUpdateTheme() {
        final ThemeEntity updateTheme = new ThemeEntity();
        updateTheme.setId(THEME_ID);
        updateTheme.setReferenceId(DOMAIN_ID);
        updateTheme.setReferenceType(ReferenceType.DOMAIN);
        updateTheme.setCss("cssUpdate");
        updateTheme.setFaviconUrl("favicon");

        final Theme theme = new Theme();
        theme.setId(THEME_ID);
        theme.setCss("css");
        theme.setFaviconUrl("favicon");
        theme.setLogoUrl("url");
        theme.setLogoWidth(222);
        theme.setReferenceId(DOMAIN_ID);
        theme.setReferenceType(ReferenceType.DOMAIN);
        theme.setPrimaryTextColorHex("#787787");
        theme.setPrimaryButtonColorHex("#877787");
        theme.setSecondaryButtonColorHex("#799955");
        final Date now = new Date();
        theme.setCreatedAt(now);
        theme.setUpdatedAt(now);

        doReturn(Maybe.just(new Domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(theme)).when(themeService).update(any(), any(), any());

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .put(Entity.json(updateTheme));

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        assertSameEntity(theme, response);

        verify(themeService).update(any(), argThat(input ->
                updateTheme.getCss().equals(input.getCss())
                        && THEME_ID.equals(input.getId())
                        && DOMAIN_ID.equals(input.getReferenceId())
                        && ReferenceType.DOMAIN.equals(input.getReferenceType())), any());
    }

    @Test
    public void shouldNoDeletedTheme_UnknownDomain() {
        doReturn(Maybe.empty()).when(domainService).findById(DOMAIN_ID);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldDeleteTheme() {
        doReturn(Maybe.just(new Domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Completable.complete()).when(themeService).delete(any(), any(), any());

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("themes")
                .path(THEME_ID)
                .request()
                .delete();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    private void assertSameEntity(Theme theme, Response response) {
        final ThemeEntity entity = readEntity(response, ThemeEntity.class);
        assertNotNull(entity);

        assertEquals(theme.getId(), entity.getId());
        assertEquals(theme.getCss(), entity.getCss());
        assertEquals(theme.getFaviconUrl(), entity.getFaviconUrl());
        assertEquals(theme.getLogoUrl(), entity.getLogoUrl());
        assertEquals(theme.getLogoWidth(), entity.getLogoWidth());
        assertEquals(theme.getReferenceId(), entity.getReferenceId());
        assertEquals(theme.getReferenceType(), entity.getReferenceType());
        assertEquals(theme.getPrimaryTextColorHex(), entity.getPrimaryTextColorHex());
        assertEquals(theme.getPrimaryButtonColorHex(), entity.getPrimaryButtonColorHex());
    }
}
