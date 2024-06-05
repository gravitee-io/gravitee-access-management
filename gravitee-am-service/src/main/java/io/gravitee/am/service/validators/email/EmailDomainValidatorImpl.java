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
package io.gravitee.am.service.validators.email;

import io.gravitee.am.service.spring.email.EmailConfiguration;
import io.gravitee.am.service.utils.WildcardUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EmailDomainValidatorImpl implements EmailDomainValidator {

    private final List<Pattern> allowList;

    public EmailDomainValidatorImpl(EmailConfiguration emailConfiguration) {
        this.allowList = emailConfiguration.getAllowedFrom().stream()
                .map(WildcardUtils::toRegex)
                .map(Pattern::compile)
                .collect(toList());
    }

    @Override
    public Boolean validate(String email) {
        return nonNull(email) && !email.isBlank() && allowList.stream()
                .map(pattern -> pattern.matcher(email))
                .anyMatch(Matcher::matches);
    }
}
