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
package io.gravitee.am.authorizationengine.api.ws;

import io.gravitee.am.authorizationengine.api.audit.AuthorizationAuditEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WsMessageCodecTest {

    private final WsMessageCodec codec = new WsMessageCodec();

    @Test
    void roundTrip_bundleCheck() {
        var original = new WsMessage.BundleCheck(42);
        String json = codec.encode(original);

        assertThat(json).contains("\"type\":\"bundle_check\"");
        assertThat(json).contains("\"version\":42");

        WsMessage decoded = codec.decode(json);
        assertThat(decoded).isInstanceOf(WsMessage.BundleCheck.class);
        assertThat(((WsMessage.BundleCheck) decoded).version()).isEqualTo(42);
    }

    @Test
    void roundTrip_bundleCurrent() {
        var original = new WsMessage.BundleCurrent();
        String json = codec.encode(original);

        assertThat(json).contains("\"type\":\"bundle_current\"");

        WsMessage decoded = codec.decode(json);
        assertThat(decoded).isInstanceOf(WsMessage.BundleCurrent.class);
    }

    @Test
    void roundTrip_bundleUpdate() {
        var original = new WsMessage.BundleUpdate(5, "permit(p,a,r);", "[{\"id\":1}]", "{\"schema\":true}");
        String json = codec.encode(original);

        assertThat(json).contains("\"type\":\"bundle_update\"");
        assertThat(json).contains("\"version\":5");

        WsMessage decoded = codec.decode(json);
        assertThat(decoded).isInstanceOf(WsMessage.BundleUpdate.class);
        var bu = (WsMessage.BundleUpdate) decoded;
        assertThat(bu.version()).isEqualTo(5);
        assertThat(bu.policy()).isEqualTo("permit(p,a,r);");
        assertThat(bu.data()).isEqualTo("[{\"id\":1}]");
        assertThat(bu.schema()).isEqualTo("{\"schema\":true}");
    }

    @Test
    void roundTrip_bundleUpdate_nullSchema() {
        var original = new WsMessage.BundleUpdate(1, "policy", "data", null);
        String json = codec.encode(original);
        WsMessage decoded = codec.decode(json);

        assertThat(decoded).isInstanceOf(WsMessage.BundleUpdate.class);
        var bu = (WsMessage.BundleUpdate) decoded;
        assertThat(bu.schema()).isNull();
    }

    @Test
    void roundTrip_auditEvent() {
        var event = new AuthorizationAuditEvent(
                "dec-1", "2025-01-01T00:00:00Z", true,
                "User", "alice", "read", "Document", "doc-1", "cedar"
        );
        var original = new WsMessage.AuditEvent(event);
        String json = codec.encode(original);

        assertThat(json).contains("\"type\":\"audit_event\"");
        assertThat(json).contains("\"decisionId\":\"dec-1\"");

        WsMessage decoded = codec.decode(json);
        assertThat(decoded).isInstanceOf(WsMessage.AuditEvent.class);
        var ae = (WsMessage.AuditEvent) decoded;
        assertThat(ae.event().decisionId()).isEqualTo("dec-1");
        assertThat(ae.event().decision()).isTrue();
        assertThat(ae.event().principalId()).isEqualTo("alice");
        assertThat(ae.event().engine()).isEqualTo("cedar");
    }

    @Test
    void roundTrip_error() {
        var original = new WsMessage.Error("AUTH_FAILED", "Invalid API key");
        String json = codec.encode(original);

        assertThat(json).contains("\"type\":\"error\"");
        assertThat(json).contains("\"code\":\"AUTH_FAILED\"");

        WsMessage decoded = codec.decode(json);
        assertThat(decoded).isInstanceOf(WsMessage.Error.class);
        var err = (WsMessage.Error) decoded;
        assertThat(err.code()).isEqualTo("AUTH_FAILED");
        assertThat(err.message()).isEqualTo("Invalid API key");
    }

    @Test
    void decode_unknownType_throws() {
        assertThatThrownBy(() -> codec.decode("{\"type\":\"unknown\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown WsMessage type");
    }

    @Test
    void decode_missingType_throws() {
        assertThatThrownBy(() -> codec.decode("{\"version\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required field: type");
    }

    @Test
    void decode_invalidJson_throws() {
        assertThatThrownBy(() -> codec.decode("not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decode");
    }

    @Test
    void decode_auditEvent_missingEvent_throws() {
        assertThatThrownBy(() -> codec.decode("{\"type\":\"audit_event\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing 'event' field");
    }
}
