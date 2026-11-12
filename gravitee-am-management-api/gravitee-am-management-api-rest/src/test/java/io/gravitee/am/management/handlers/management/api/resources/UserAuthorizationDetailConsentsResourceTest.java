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

import com.fasterxml.jackson.core.type.TypeReference;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.handlers.management.api.model.AuthorizationDetailApprovalEntity;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.management.handlers.management.api.resources.model.CursorPagination;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.cursor.CursorRequest;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
class UserAuthorizationDetailConsentsResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String USER_ID = "user-id-1";

    private Domain domain;

    @BeforeEach
    void setUpDomain() {
        domain = new Domain();
        domain.setId(DOMAIN_ID);
        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
        reset(authorizationDetailApprovalAdapter);
    }

    @Test
    void shouldListTheFirstPageNewestFirst() {
        AuthorizationDetailApproval approval = new AuthorizationDetailApproval();
        approval.setUserId(UserId.internal(USER_ID));
        approval.setClientId("client-id");
        approval.setDetail(Map.of("type", "payment_initiation"));
        givenConsents(List.of(new AuthorizationDetailApprovalEntity(approval)), null, 1L);

        Response response = consents().request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.OK_200);
        Map<String, Object> body = readEntity(response, new TypeReference<>() {});
        assertThat(body).containsEntry("totalCount", 1).containsEntry("page", 0).doesNotContainKey("nextCursor");
        assertThat((List<Map<String, Object>>) body.get("data")).singleElement().satisfies(consent -> {
            assertThat(consent).containsEntry("type", "payment_initiation").containsEntry("clientId", "client-id");
            assertThat(consent.get("detail")).isEqualTo(Map.of("type", "payment_initiation"));
        });
        AuthorizationDetailApprovalCursorRequest cursor = requestedCursor(CursorPagination.DEFAULT_LIMIT);
        assertThat(cursor.isFirstPage()).isTrue();
        assertThat(cursor.getSortField()).isEqualTo("updatedAt");
        assertThat(cursor.getSortDirection()).isEqualTo(CursorRequest.SortDirection.DESC);
        assertThat(cursor.getClientId()).isNull();
        assertThat(cursor.getType()).isNull();
    }

    @Test
    void shouldPassTheFiltersAndSort() {
        givenConsents(List.of(), null, 0L);

        Response response = consents()
                .queryParam("clientId", "client-id")
                .queryParam("type", "payment_initiation")
                .queryParam("sort", "expiresAt")
                .queryParam("dir", "ASC")
                .queryParam("limit", 500)
                .request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.OK_200);
        AuthorizationDetailApprovalCursorRequest cursor = requestedCursor(CursorPagination.MAX_LIMIT);
        assertThat(cursor.getClientId()).isEqualTo("client-id");
        assertThat(cursor.getType()).isEqualTo("payment_initiation");
        assertThat(cursor.getSortField()).isEqualTo("expiresAt");
        assertThat(cursor.getSortDirection()).isEqualTo(CursorRequest.SortDirection.ASC);
    }

    @Test
    void shouldContinueFromTheNextCursor() {
        var next = new AuthorizationDetailApprovalCursorRequest("1700000000000", "approval-2", CursorRequest.SortDirection.DESC,
                "updatedAt", 1, null, "payment_initiation");
        givenConsents(List.of(), next, 3L);

        Map<String, Object> firstPage = readEntity(consents().queryParam("type", "payment_initiation").request().get(), new TypeReference<>() {});
        String nextCursor = (String) firstPage.get("nextCursor");
        assertThat(nextCursor).startsWith("/organizations/DEFAULT/environments/DEFAULT/domains/" + DOMAIN_ID
                + "/users/" + USER_ID + "/consents/authorization-details?").contains("page=1").contains("cursor=");

        String cursorParam = nextCursor.replaceAll(".*[?&]cursor=([^&]+).*", "$1");
        Response response = consents().queryParam("type", "payment_initiation").queryParam("page", 1)
                .queryParam("cursor", cursorParam).request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.OK_200);
        ArgumentCaptor<AuthorizationDetailApprovalCursorRequest> cursors = ArgumentCaptor.forClass(AuthorizationDetailApprovalCursorRequest.class);
        verify(authorizationDetailApprovalAdapter, times(2)).getUserConsents(eq(domain), eq(USER_ID), cursors.capture(), anyInt());
        AuthorizationDetailApprovalCursorRequest continued = cursors.getAllValues().get(1);
        assertThat(continued.getLastId()).isEqualTo("approval-2");
        assertThat(continued.getLastSortValue()).isEqualTo("1700000000000");
        assertThat(continued.getPage()).isEqualTo(1);
        assertThat(continued.getType()).isEqualTo("payment_initiation");
    }

    @Test
    void shouldRejectAnUnknownSortField() {
        Response response = consents().queryParam("sort", "detail").request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.BAD_REQUEST_400);
        verify(authorizationDetailApprovalAdapter, never()).getUserConsents(any(), any(), any(), anyInt());
    }

    @Test
    void shouldRejectAnInvalidCursor() {
        Response response = consents().queryParam("cursor", "not-a-cursor").request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.BAD_REQUEST_400);
        verify(authorizationDetailApprovalAdapter, never()).getUserConsents(any(), any(), any(), anyInt());
    }

    @Test
    void shouldRejectAUserWithoutPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        Response response = consents().request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.FORBIDDEN_403);
        verify(authorizationDetailApprovalAdapter, never()).getUserConsents(any(), any(), any(), anyInt());
    }

    @Test
    void shouldReturnNotFoundForAnUnknownUser() {
        doReturn(Single.error(new UserNotFoundException(UserId.internal(USER_ID))))
                .when(authorizationDetailApprovalAdapter).getUserConsents(eq(domain), eq(USER_ID), any(), anyInt());

        Response response = consents().request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.NOT_FOUND_404);
    }

    @Test
    void shouldReturnAnInternalErrorOnTechnicalFailure() {
        doReturn(Single.error(new TechnicalManagementException("unavailable")))
                .when(authorizationDetailApprovalAdapter).getUserConsents(eq(domain), eq(USER_ID), any(), anyInt());

        Response response = consents().request().get();

        assertThat(response.getStatus()).isEqualTo(HttpStatusCode.INTERNAL_SERVER_ERROR_500);
    }

    private void givenConsents(List<AuthorizationDetailApprovalEntity> data, AuthorizationDetailApprovalCursorRequest next, Long totalCount) {
        doReturn(Single.just(new CursorPage<>(data, next, totalCount)))
                .when(authorizationDetailApprovalAdapter).getUserConsents(eq(domain), eq(USER_ID), any(), anyInt());
    }

    private AuthorizationDetailApprovalCursorRequest requestedCursor(int limit) {
        ArgumentCaptor<AuthorizationDetailApprovalCursorRequest> cursor = ArgumentCaptor.forClass(AuthorizationDetailApprovalCursorRequest.class);
        verify(authorizationDetailApprovalAdapter).getUserConsents(eq(domain), eq(USER_ID), cursor.capture(), eq(limit));
        return cursor.getValue();
    }

    private WebTarget consents() {
        return target("domains").path(DOMAIN_ID).path("users").path(USER_ID).path("consents").path("authorization-details");
    }
}
