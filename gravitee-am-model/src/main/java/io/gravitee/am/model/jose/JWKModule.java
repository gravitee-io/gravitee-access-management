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
package io.gravitee.am.model.jose;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;

/**
 * Teaches a plain {@link com.fasterxml.jackson.databind.ObjectMapper} to read a {@link JWK} back
 * from JSON by dispatching on the {@code kty} member RFC 7517 mandates. Register it on any mapper
 * that has to deserialize key material.
 *
 * @author GraviteeSource Team
 */
public class JWKModule extends SimpleModule {

    public JWKModule() {
        addDeserializer(JWK.class, new JWKDeserializer());
    }

    private static class JWKDeserializer extends StdDeserializer<JWK> {

        JWKDeserializer() {
            super(JWK.class);
        }

        @Override
        public JWK deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            JsonNode kty = node.get("kty");
            if (kty == null || kty.isNull()) {
                throw new IOException("JWK is missing its kty member");
            }
            Class<? extends JWK> keyType = switch (KeyType.parse(kty.asText())) {
                case RSA -> RSAKey.class;
                case EC -> ECKey.class;
                case OCT -> OCTKey.class;
                case OKP -> OKPKey.class;
            };
            return parser.getCodec().treeToValue(node, keyType);
        }
    }
}
