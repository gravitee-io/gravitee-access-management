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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl;

import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;

/**
 * Validator for OAuth 2.0 access tokens issued by this domain.
 *
 * @author GraviteeSource Team
 */
public class AccessTokenValidator extends AbstractTokenValidator {

    @Override
    protected JWTService.TokenType tokenType() {
        return JWTService.TokenType.ACCESS_TOKEN;
    }

    @Override
    protected String tokenTypeUrn() {
        return TokenType.ACCESS_TOKEN;
    }
}
