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
package io.gravitee.am.gateway.handler.common.jwk.converter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.util.StdConverter;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWKSetDeserializer extends StdConverter<ObjectNode, Optional<JWKSet>> {

    private final static Logger LOGGER = LoggerFactory.getLogger(JWKSetDeserializer.class);
    public final static String PARSE_ERROR_MESSAGE = "Unable to parse jwks content: ";

    @Override
    public Optional<JWKSet> convert(ObjectNode node) {
        if (node == null) {
            return null;
        }

        if (node.get("keys") == null ||
                node.get("keys").isNull() ||
                node.get("keys").equals(new TextNode("null")) ||
                node.get("keys").equals(new TextNode(""))
        ) {
            return Optional.empty();
        }

        return convert(node.toString());
    }

    public Optional<JWKSet> convert(String jwkSetAsString) {
        try {
            com.nimbusds.jose.jwk.JWKSet jwkSet = com.nimbusds.jose.jwk.JWKSet.parse(jwkSetAsString);
            List<JWK> jwkList = jwkSet.getKeys()
                    .stream()
                    .map(JWKConverter::convert)
                    .collect(Collectors.toList());

            JWKSet result = new JWKSet();
            result.setKeys(jwkList);
            return Optional.of(result);
        } catch (ParseException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new InvalidClientMetadataException(PARSE_ERROR_MESSAGE+ex.getMessage());
        }
    }
}
