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
package io.gravitee.am.management.handlers.management.api.resources.dashboard;

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.TotalToken;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Single;
import org.junit.Test;

import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DashboardTokensResourceTest extends JerseySpringTest {

    @Test
    public void shouldListTotalTokens() {
        final TotalToken totalToken = new TotalToken();
        totalToken.setTotalAccessTokens(10l);

        doReturn(Single.just(totalToken)).when(tokenService).findTotalTokens();
        final Response response = target("dashboard")
                .path("tokens")
                .request()
                .get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final TotalToken responseEntity = response.readEntity(TotalToken.class);

        assertEquals(10l, responseEntity.getTotalAccessTokens());
    }

    @Test
    public void shouldListTotalTokensByDomain() {
        final String domainId = "domain-1";
        final TotalToken totalToken = new TotalToken();
        totalToken.setTotalAccessTokens(10l);

        doReturn(Single.just(totalToken)).when(tokenService).findTotalTokensByDomain(domainId);
        final Response response = target("dashboard")
                .path("tokens")
                .queryParam("domainId", domainId)
                .request()
                .get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        final TotalToken responseEntity = response.readEntity(TotalToken.class);

        assertEquals(10l, responseEntity.getTotalAccessTokens());
    }

    @Test
    public void shouldListTotalTokens_technicalManagementException() {
        doReturn(Single.error(new TechnicalManagementException("Error occurs"))).when(tokenService).findTotalTokens();
        final Response response = target("dashboard")
                .path("tokens")
                .request()
                .get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

}
