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
package io.gravitee.am.management.service.assertions;

import io.gravitee.am.management.service.impl.utils.MimeMessageParser;
import jakarta.mail.Address;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.internal.Iterables;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Aurelien PACAUD (aurelien.pacaud at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MimeMessageParserAssert extends AbstractAssert<MimeMessageParserAssert, MimeMessageParser> {

    public MimeMessageParserAssert(MimeMessageParser mimeMessageParser) {
        super(mimeMessageParser, MimeMessageParserAssert.class);
    }

    public static MimeMessageParserAssert assertThat(MimeMessageParser actual) {
        return new MimeMessageParserAssert(actual);
    }

    public MimeMessageParserAssert hasFrom(String from) throws Exception {
        isNotNull();

        String assertjErrorMessage = "\nExpecting From to be:\n  <%s>\nbut was:\n  <%s>";

        if (!actual.getFrom().equals(from)) {
            failWithMessage(assertjErrorMessage, from, actual.getFrom());
        }
        return this;
    }

    public MimeMessageParserAssert hasTo(String... to) throws Exception {
        return hasTo(Arrays.asList(to));
    }

    public MimeMessageParserAssert hasTo(List<String> to) throws Exception {
        isNotNull();

        if (to == null) {
            failWithMessage("Expecting to parameter not to be null.");
            return this;
        }

        Iterables.instance().assertContains(info, actual.getTo().stream().map(Address::toString).collect(Collectors.toList()), to.toArray());

        return this;
    }

    public MimeMessageParserAssert hasSubject(String subject) throws Exception {
        isNotNull();
        String assertjErrorMessage = "\nExpecting Subject to be:\n  <%s>\nbut was:\n  <%s>";

        if (!actual.getSubject().equals(subject)) {
            failWithMessage(assertjErrorMessage, subject, actual.getSubject());
        }
        return this;
    }

    public MimeMessageParserAssert hasHtmlContent(String html) {
        isNotNull();
        Assertions.assertThat(actual.getHtmlContent()).isEqualToIgnoringWhitespace(html);
        return this;
    }
}
