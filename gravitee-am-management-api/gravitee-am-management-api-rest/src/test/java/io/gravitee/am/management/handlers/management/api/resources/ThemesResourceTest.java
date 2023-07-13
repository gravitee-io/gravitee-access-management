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
import io.gravitee.am.service.model.NewTheme;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThemesResourceTest extends JerseySpringTest {

    @Before
    public void initialize() {
        reset(themeService);
    }

    @Test
    public void shouldNotListThemes_UnkownDomain() {
        final String domainId = "domain-1";
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("themes")
                .request()
                .get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldListThemes() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.just(new Theme())).when(themeService).findByReference(ReferenceType.DOMAIN, domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("themes")
                .request()
                .get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        assertEquals(1, response.readEntity(List.class).size());
    }

    @Test
    public void shouldListThemes_NoContent() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(themeService).findByReference(ReferenceType.DOMAIN, domainId);

        final Response response = target("domains")
                .path(domainId)
                .path("themes")
                .request()
                .get();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldNotCreateThemes_UnkownDomain() {
        final String domainId = "domain-1";
        doReturn(Maybe.empty()).when(domainService).findById(domainId);

        final NewTheme newTheme = new NewTheme();

        final Response response = target("domains")
                .path(domainId)
                .path("themes")
                .request()
                .post(Entity.json(newTheme));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotCreateThemes_MissingTheme() {
        final String domainId = "domain-1";
        final Response response = target("domains")
                .path(domainId)
                .path("themes")
                .request()
                .post(Entity.json(null));

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldCreateTheme() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        final Theme theme = new Theme();
        theme.setId("themeid");
        theme.setCss("css");
        theme.setFaviconUrl("favicon");
        theme.setLogoUrl("url");
        theme.setLogoWidth(222);
        theme.setReferenceId(domainId);
        theme.setReferenceType(ReferenceType.DOMAIN);
        theme.setPrimaryTextColorHex("#787787");
        theme.setPrimaryButtonColorHex("#877787");
        theme.setSecondaryButtonColorHex("#799955");
        theme.setSecondaryTextColorHex("#795955");
        final Date now = new Date();
        theme.setCreatedAt(now);
        theme.setUpdatedAt(now);

        doReturn(Single.just(theme)).when(themeService).create(any(), any(), any());

        final Response response = target("domains")
                .path(domainId)
                .path("themes")
                .request()
                .post(Entity.json(new NewTheme()));

        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
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
        assertEquals(theme.getSecondaryButtonColorHex(), entity.getSecondaryButtonColorHex());
        assertEquals(theme.getSecondaryTextColorHex(), entity.getSecondaryTextColorHex());

        assertNotNull(response.getHeaderString(HttpHeaders.LOCATION));
        assertTrue(response.getHeaderString(HttpHeaders.LOCATION).endsWith("/themes/"+theme.getId()));
    }

}
