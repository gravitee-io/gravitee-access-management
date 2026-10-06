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
package io.gravitee.am.gateway.handler.oauth2.service.validation;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidAuthorizationDetailsException;

import java.util.Collection;

/**
 * Validates the authorization_details claim of a redeemed ID-JAG.
 * <p>
 * draft-ietf-oauth-identity-assertion-authz-grant-04 section 4.4.1 requires the claim to be processed
 * according to RFC 9396 (Rich Authorization Requests). RFC 9396 is not implemented for this grant yet:
 * AM knows no authorization details type, so every type is unknown and RFC 9396 sections 5 and 8
 * require the request to be refused with invalid_authorization_details. An absent or empty array
 * requests nothing and is accepted.
 * <p>
 * Supporting RFC 9396 replaces the unsupported type refusal with per-type validation and requires the
 * granted authorization details to be returned in the token response; the malformed check stays.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/draft-ietf-oauth-identity-assertion-authz-grant-04#section-4.4.1">ID-JAG section 4.4.1</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9396.html#section-5">RFC 9396 section 5</a>
 */
public class AuthorizationDetailsValidator {

    public void validate(Object authorizationDetails) {
        switch (authorizationDetails) {
            case null -> {
            }
            case Collection<?> details when details.isEmpty() -> {
            }
            case Collection<?> details -> throw new InvalidAuthorizationDetailsException("Assertion authorization_details type is not supported");
            default -> throw new InvalidAuthorizationDetailsException("Assertion authorization_details is malformed");
        }
    }
}
