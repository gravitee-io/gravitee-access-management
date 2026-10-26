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
package io.gravitee.am.service.model;

import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PatchAuthorizationDetailTypeTest {

    private static final Date CREATED_AT = new Date(1_000L);

    @Test
    void shouldLeaveEveryFieldUnchangedWhenNothingIsSent() {
        AuthorizationDetailType existing = existing();

        AuthorizationDetailType patched = new PatchAuthorizationDetailType().patch(existing);

        assertThat(patched).isEqualTo(existing).isNotSameAs(existing);
    }

    @Test
    void shouldSetEveryFieldSent() {
        PatchAuthorizationDetailType patch = new PatchAuthorizationDetailType();
        patch.setName(Optional.of("Renamed"));
        patch.setDescription(Optional.of("New description"));
        patch.setSchema(Optional.of("{\"type\":\"object\"}"));
        patch.setDiscovery(Optional.of(false));
        patch.setExpiresIn(Optional.of(60));

        AuthorizationDetailType patched = patch.patch(existing());

        assertThat(patched.getName()).isEqualTo("Renamed");
        assertThat(patched.getDescription()).isEqualTo("New description");
        assertThat(patched.getSchema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(patched.isDiscovery()).isFalse();
        assertThat(patched.getExpiresIn()).isEqualTo(60);
        assertThat(patched.getId()).isEqualTo("id-1");
        assertThat(patched.getDomain()).isEqualTo("domain-1");
        assertThat(patched.getType()).isEqualTo("payment_initiation");
        assertThat(patched.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void shouldChangeOnlyDiscoveryWhenOnlyDiscoveryIsSent() {
        PatchAuthorizationDetailType patch = new PatchAuthorizationDetailType();
        patch.setDiscovery(Optional.of(false));

        AuthorizationDetailType patched = patch.patch(existing());

        assertThat(patched).isEqualTo(existing().toBuilder().discovery(false).build());
    }

    @Test
    void shouldClearOptionalFieldsSentAsNull() {
        PatchAuthorizationDetailType patch = new PatchAuthorizationDetailType();
        patch.setDescription(Optional.empty());
        patch.setSchema(Optional.empty());
        patch.setExpiresIn(Optional.empty());

        AuthorizationDetailType patched = patch.patch(existing());

        assertThat(patched.getDescription()).isNull();
        assertThat(patched.getSchema()).isNull();
        assertThat(patched.getExpiresIn()).isNull();
        assertThat(patched.getName()).isEqualTo("Payment initiation");
        assertThat(patched.isDiscovery()).isTrue();
    }

    private static AuthorizationDetailType existing() {
        return AuthorizationDetailType.builder()
                .id("id-1")
                .domain("domain-1")
                .type("payment_initiation")
                .name("Payment initiation")
                .description("Initiates a payment")
                .schema("{\"type\":\"object\",\"properties\":{\"type\":{\"const\":\"payment_initiation\"}},\"required\":[\"type\"]}")
                .discovery(true)
                .expiresIn(3600)
                .createdAt(CREATED_AT)
                .updatedAt(CREATED_AT)
                .build();
    }
}
