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
package io.gravitee.am.extensiongrant.tokenexchange.validation;

import io.gravitee.am.common.oauth2.TokenTypeURN;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.reactivex.rxjava3.core.Single;

/**
 * Validator for Refresh Tokens (urn:ietf:params:oauth:token-type:refresh_token).
 */
public class RefreshTokenValidator implements SubjectTokenValidator {

    private final JwtSubjectTokenValidator delegate = new JwtSubjectTokenValidator(TokenTypeURN.REFRESH_TOKEN);

    @Override
    public Single<ValidatedToken> validate(String token, TokenExchangeExtensionGrantConfiguration configuration) throws InvalidGrantException {
        return delegate.validate(token, configuration);
    }

    @Override
    public String getSupportedTokenType() {
        return TokenTypeURN.REFRESH_TOKEN;
    }
}
