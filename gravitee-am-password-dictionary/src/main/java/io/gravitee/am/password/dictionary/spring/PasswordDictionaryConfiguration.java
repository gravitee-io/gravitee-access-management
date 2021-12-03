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

package io.gravitee.am.password.dictionary.spring;

import io.gravitee.am.password.dictionary.PasswordDictionary;
import io.gravitee.am.password.dictionary.PasswordDictionaryImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class PasswordDictionaryConfiguration {

    @Value("${user.password.policy.dictionary.filename:#{null}}")
    private String passwordDictionaryFilename;

    @Value("${user.password.policy.dictionary.watch:false}")
    private boolean watch;

    @Bean
    public PasswordDictionary passwordDictionary() {
        return new PasswordDictionaryImpl(passwordDictionaryFilename).start(watch);
    }
}
