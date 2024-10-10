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
package io.gravitee.am.gateway.handler.oauth2.service.introspection;

import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.RefreshToken;
import lombok.NonNull;
import lombok.Value;

import java.util.Optional;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */

@Value
public class IntrospectionRequest {

    @NonNull String token;
    TokenTypeHint tokenTypeHint;

    public Optional<TokenTypeHint> getTokenTypeHint() {
        return Optional.ofNullable(tokenTypeHint);
    }

    public static IntrospectionRequest withHint(String token, String hint){
        return new IntrospectionRequest(token, TokenTypeHint.toOptional(hint).orElse(null));
    }

    public static IntrospectionRequest withoutHint(String token){
        return new IntrospectionRequest(token, null);
    }

}
