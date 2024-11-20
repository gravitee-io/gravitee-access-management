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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.cert.CertificateException;
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
  
}