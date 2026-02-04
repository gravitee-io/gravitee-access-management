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
package io.gravitee.am.gateway.handler.oauth2.service.token.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.ExchangedIDToken;

import java.io.IOException;

/**
 * JSON serializer for {@link ExchangedIDToken}.
 * <p>
 * Per RFC 8693, the ID token response includes only:
 * <ul>
 *   <li>access_token - contains the ID token JWT</li>
 *   <li>token_type - "N_A" for non-access tokens</li>
 *   <li>expires_in - lifetime in seconds</li>
 *   <li>issued_token_type - "urn:ietf:params:oauth:token-type:id_token"</li>
 * </ul>
 * <p>
 * Notably absent (compared to AccessTokenSerializer):
 * <ul>
 *   <li>scope - ID tokens are for identity, not authorization</li>
 *   <li>refresh_token - not applicable for ID token exchange</li>
 *   <li>cnf (confirmation method) - not applicable for ID tokens</li>
 * </ul>
 *
 * @author GraviteeSource Team
 */
public class ExchangedIDTokenSerializer extends StdSerializer<ExchangedIDToken> {

    public ExchangedIDTokenSerializer() {
        super(ExchangedIDToken.class);
    }

    @Override
    public void serialize(ExchangedIDToken token, JsonGenerator jsonGenerator, SerializerProvider provider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(Token.ACCESS_TOKEN, token.getValue());
        jsonGenerator.writeStringField(Token.TOKEN_TYPE, token.getTokenType());
        jsonGenerator.writeNumberField(Token.EXPIRES_IN, token.getExpiresIn());

        if (token.getIssuedTokenType() != null) {
            jsonGenerator.writeStringField(Token.ISSUED_TOKEN_TYPE, token.getIssuedTokenType());
        }

        jsonGenerator.writeEndObject();
    }
}
