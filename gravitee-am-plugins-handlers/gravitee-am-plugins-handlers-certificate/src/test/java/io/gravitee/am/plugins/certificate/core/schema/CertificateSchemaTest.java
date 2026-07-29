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
package io.gravitee.am.plugins.certificate.core.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
public class CertificateSchemaTest {

    @Test
    void should_return_file_key_of_file_widget() throws CertificateException {
        var schema = new CertificateSchema();
        var fileProperty = new CertificateSchemaProperty();
        fileProperty.setWidget("file");
        schema.setProperties(Map.of("key", fileProperty));

        Optional<String> fileKey = schema.getFileKey();
        Assertions.assertTrue(fileKey.isPresent());
        Assertions.assertEquals("key", fileKey.get());
    }

    @Test
    void should_return_empty_if_file_widget_is_missing() throws CertificateException {
        var schema = new CertificateSchema();
        schema.setProperties(Map.of());

        Optional<String> fileKey = schema.getFileKey();
        Assertions.assertTrue(fileKey.isEmpty());
    }

    @Test
    void shouldIgnoreUnknownPropertiesLikeAllOf() throws Exception {
        var json = """
                {
                    "type": "object",
                    "id": "urn:jsonschema:io:gravitee:am:certificate:oci:OCIConfiguration",
                    "description": "OCI certificate",
                    "properties": {
                        "authMethod": { "type": "string", "title": "Authentication method" },
                        "privateKey": { "type": "string", "widget": "file" }
                    },
                    "required": ["authMethod"],
                    "allOf": [
                        {
                            "if": {
                                "properties": { "authMethod": { "const": "API_PRIVATE_KEY" } },
                                "required": ["authMethod"]
                            },
                            "then": {
                                "properties": {
                                    "tenancyOcid": { "minLength": 1 },
                                    "userOcid": { "minLength": 1 },
                                    "fingerprint": { "minLength": 1 },
                                    "region": { "minLength": 1 },
                                    "privateKey": { "minLength": 1 }
                                },
                                "required": ["tenancyOcid", "userOcid", "fingerprint", "region", "privateKey"]
                            }
                        },
                        {
                            "if": {
                                "properties": { "authMethod": { "const": "CONFIG_FILE" } },
                                "required": ["authMethod"]
                            },
                            "then": {
                                "properties": { "configFilePath": { "minLength": 1 } },
                                "required": ["configFilePath"]
                            }
                        }
                    ]
                }
                """;

        var schema = new ObjectMapper().readValue(json, CertificateSchema.class);

        assertEquals("object", schema.getType());
        assertEquals(List.of("authMethod"), schema.getRequired());
        assertEquals(2, schema.getProperties().size());
        assertEquals("file", schema.getProperties().get("privateKey").getWidget());
        assertEquals(Optional.of("privateKey"), schema.getFileKey());
    }

}