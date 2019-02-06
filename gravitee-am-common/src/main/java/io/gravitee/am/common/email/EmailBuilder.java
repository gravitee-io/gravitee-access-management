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
package io.gravitee.am.common.email;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailBuilder {

    private final Email email = new Email();
    private static final String TEMPLATE_SUFFIX = ".html";

    public EmailBuilder from(String from) {
        this.email.setFrom(from);
        return this;
    }

    public EmailBuilder fromName(String fromName) {
        this.email.setFromName(fromName);
        return this;
    }

    public EmailBuilder to(String... to) {
        this.email.setTo(to);
        return this;
    }

    public EmailBuilder template(String template) {
        this.email.setTemplate(template.endsWith(TEMPLATE_SUFFIX) ? template : template + TEMPLATE_SUFFIX);
        return this;
    }

    public EmailBuilder subject(String subject) {
        this.email.setSubject(subject);
        return this;
    }

    public EmailBuilder param(String key, Object value) {
        this.email.getParams().put(key, value);
        return this;
    }

    public EmailBuilder params(Map<String, Object> params) {
        this.email.setParams(params);
        return this;
    }

    public Email build() {
        return this.email;
    }
}
