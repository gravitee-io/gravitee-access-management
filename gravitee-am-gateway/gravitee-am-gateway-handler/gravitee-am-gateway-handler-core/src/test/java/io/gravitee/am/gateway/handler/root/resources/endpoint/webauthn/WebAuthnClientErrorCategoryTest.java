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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnClientErrorCategoryTest {

    @Test
    public void shouldMapNullOrBlankToUnknown() {
        assertEquals(WebAuthnClientErrorCategory.UNKNOWN, WebAuthnClientErrorCategory.fromTechnicalErrorName(null));
        assertEquals(WebAuthnClientErrorCategory.UNKNOWN, WebAuthnClientErrorCategory.fromTechnicalErrorName(""));
        assertEquals(WebAuthnClientErrorCategory.UNKNOWN, WebAuthnClientErrorCategory.fromTechnicalErrorName("   "));
    }

    @Test
    public void shouldMapUserCancelOrTimeout() {
        assertEquals(WebAuthnClientErrorCategory.USER_CANCEL_OR_TIMEOUT, WebAuthnClientErrorCategory.fromTechnicalErrorName("NotAllowedError"));
        assertEquals(WebAuthnClientErrorCategory.USER_CANCEL_OR_TIMEOUT, WebAuthnClientErrorCategory.fromTechnicalErrorName("AbortError"));
    }

    @Test
    public void shouldMapSecurityIssue() {
        assertEquals(WebAuthnClientErrorCategory.SECURITY_ISSUE, WebAuthnClientErrorCategory.fromTechnicalErrorName("SecurityError"));
    }

    @Test
    public void shouldMapNotSupported() {
        assertEquals(WebAuthnClientErrorCategory.NOT_SUPPORTED, WebAuthnClientErrorCategory.fromTechnicalErrorName("NotSupportedError"));
    }

    @Test
    public void shouldMapAuthenticatorFailure() {
        assertEquals(WebAuthnClientErrorCategory.AUTHENTICATOR_FAILURE, WebAuthnClientErrorCategory.fromTechnicalErrorName("InvalidStateError"));
    }

    @Test
    public void shouldMapInvalidRequest() {
        assertEquals(WebAuthnClientErrorCategory.INVALID_REQUEST, WebAuthnClientErrorCategory.fromTechnicalErrorName("ConstraintError"));
        assertEquals(WebAuthnClientErrorCategory.INVALID_REQUEST, WebAuthnClientErrorCategory.fromTechnicalErrorName("EncodingError"));
        assertEquals(WebAuthnClientErrorCategory.INVALID_REQUEST, WebAuthnClientErrorCategory.fromTechnicalErrorName("SyntaxError"));
        assertEquals(WebAuthnClientErrorCategory.INVALID_REQUEST, WebAuthnClientErrorCategory.fromTechnicalErrorName("TypeError"));
    }

    @Test
    public void shouldMapUnknownTechnicalNamesToUnknown() {
        assertEquals(WebAuthnClientErrorCategory.UNKNOWN, WebAuthnClientErrorCategory.fromTechnicalErrorName("SomeFutureError"));
    }
}
