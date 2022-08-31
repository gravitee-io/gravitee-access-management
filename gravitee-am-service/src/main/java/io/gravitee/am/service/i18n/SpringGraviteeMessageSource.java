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
package io.gravitee.am.service.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;

import java.util.Locale;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SpringGraviteeMessageSource extends GraviteeMessageResolver implements MessageSource {

    public SpringGraviteeMessageSource(FileSystemDictionaryProvider fileSystemDictionaryProvider, DynamicDictionaryProvider domainBasedDictionaryProvider)  {
        super(fileSystemDictionaryProvider, domainBasedDictionaryProvider);
    }

    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        return innerResolveMessage(locale, code);
    }

    @Override
    public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
        return innerResolveMessage(locale, code);
    }

    @Override
    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        if (resolvable.getCodes() != null && resolvable.getCodes().length > 0) {
            return innerResolveMessage(locale, resolvable.getCodes()[0]);
        } else {
            return resolvable.getDefaultMessage();
        }
    }
}
