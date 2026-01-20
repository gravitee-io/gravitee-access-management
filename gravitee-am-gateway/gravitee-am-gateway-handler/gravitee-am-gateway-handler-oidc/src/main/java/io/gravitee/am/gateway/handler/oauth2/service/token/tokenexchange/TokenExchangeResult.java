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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange;

import io.gravitee.am.model.User;

import java.util.Date;

/**
 * Result of token exchange validation containing all data needed for token creation.
 *
 * @param user the user representing the subject of the exchanged token
 * @param issuedTokenType the type of token being issued (e.g., access_token)
 * @param exchangeExpiration the expiration time from the subject token
 * @param subjectTokenId the ID of the subject token (if available)
 * @param subjectTokenType the type of the subject token (e.g., access_token, id_token)
 */
public record TokenExchangeResult(
        User user,
        String issuedTokenType,
        Date exchangeExpiration,
        String subjectTokenId,
        String subjectTokenType
) {}
