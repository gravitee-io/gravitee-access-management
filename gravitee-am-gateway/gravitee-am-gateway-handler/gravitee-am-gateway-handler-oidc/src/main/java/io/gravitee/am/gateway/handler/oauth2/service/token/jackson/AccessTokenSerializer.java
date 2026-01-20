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
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;

import java.io.IOException;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccessTokenSerializer extends StdSerializer<AccessToken> {

    public AccessTokenSerializer() {
        super(AccessToken.class);
    }

    @Override
    public void serialize(AccessToken token, JsonGenerator jsonGenerator, SerializerProvider provider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(Token.ACCESS_TOKEN, token.getValue());
        jsonGenerator.writeStringField(Token.TOKEN_TYPE, token.getTokenType());
        jsonGenerator.writeNumberField(Token.EXPIRES_IN, token.getExpiresIn());
        if (token.getIssuedTokenType() != null) {
            jsonGenerator.writeStringField(Token.ISSUED_TOKEN_TYPE, token.getIssuedTokenType());
        }

        if (token.getScope() != null) {
            jsonGenerator.writeStringField(Token.SCOPE, token.getScope());
        }

        if (token.getRefreshToken() != null) {
            jsonGenerator.writeStringField(Token.REFRESH_TOKEN, token.getRefreshToken());
        }

        if (token.getAdditionalInformation() != null) {
            Map<String, Object> additionalInformation = token.getAdditionalInformation();
            for (Map.Entry<String,Object> info : additionalInformation.entrySet()) {
                jsonGenerator.writeObjectField(info.getKey(), info.getValue());
            }
        }

        if (token.getUpgraded()!=null) {
            jsonGenerator.writeBooleanField(Token.UPGRADED, token.getUpgraded());
        }

        jsonGenerator.writeEndObject();
    }
}
