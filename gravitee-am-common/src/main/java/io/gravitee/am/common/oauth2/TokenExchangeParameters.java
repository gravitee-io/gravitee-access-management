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
package io.gravitee.am.common.oauth2;

/**
 * OAuth 2.0 Token Exchange Request Parameters (RFC 8693)
 *
 * See <a href="https://tools.ietf.org/html/rfc8693#section-2.1">RFC 8693 Section 2.1 - Request</a>
 *
 * @author GraviteeSource Team
 */
public interface TokenExchangeParameters {

    /**
     * REQUIRED. A security token that represents the identity of the party on behalf of whom
     * the request is being made. Typically, the subject of this token will be the subject of
     * the security token issued in response to the token exchange request.
     */
    String SUBJECT_TOKEN = "subject_token";

    /**
     * REQUIRED. An identifier, as described in Section 3, that indicates the type of the
     * security token in the subject_token parameter.
     */
    String SUBJECT_TOKEN_TYPE = "subject_token_type";

    /**
     * OPTIONAL. A security token that represents the identity of the acting party. Typically,
     * this will be the party that is authorized to use the requested security token and act
     * on behalf of the subject.
     */
    String ACTOR_TOKEN = "actor_token";

    /**
     * REQUIRED when actor_token is present. An identifier, as described in Section 3, that
     * indicates the type of the security token in the actor_token parameter.
     */
    String ACTOR_TOKEN_TYPE = "actor_token_type";

    /**
     * OPTIONAL. An identifier, as described in Section 3, for the type of the requested
     * security token. If the requested type is unspecified, the issued token type is at
     * the discretion of the authorization server and may be dictated by knowledge of the
     * requirements of the service or resource indicated by the resource or audience parameter.
     */
    String REQUESTED_TOKEN_TYPE = "requested_token_type";

    /**
     * OPTIONAL. A URI that indicates the target service or resource where the client intends
     * to use the requested security token. This enables the authorization server to apply
     * policy as appropriate for the target, such as determining the type and content of the
     * token to be issued or scopes and claims to be returned.
     */
    String RESOURCE = "resource";

    /**
     * OPTIONAL. The logical name of the target service where the client intends to use the
     * requested security token. This serves a purpose similar to the resource parameter but
     * with the client providing a logical name for the target service.
     */
    String AUDIENCE = "audience";

    /**
     * Response parameter. An identifier for the representation of the issued security token.
     */
    String ISSUED_TOKEN_TYPE = "issued_token_type";
}
