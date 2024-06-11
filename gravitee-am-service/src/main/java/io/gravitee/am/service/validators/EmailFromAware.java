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
package io.gravitee.am.service.validators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.validators.email.EmailDomainValidator;

import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class EmailFromAware {

    private static final String FROM_KEY = "from";
    private final EmailDomainValidator emailDomainValidator;
    private final ObjectMapper objectMapper;

    protected EmailFromAware(ObjectMapper objectMapper, EmailDomainValidator emailDomainValidator) {
        this.emailDomainValidator = emailDomainValidator;
        this.objectMapper = objectMapper;
    }


    protected boolean isValid(String from) {
        return emailDomainValidator.validate(from);
    }

    protected String getFrom(String configuration) throws JsonProcessingException {
        var tree = objectMapper.readTree(configuration);
        return ofNullable(tree.get(FROM_KEY)).map(JsonNode::textValue).orElse(null);
    }
}
