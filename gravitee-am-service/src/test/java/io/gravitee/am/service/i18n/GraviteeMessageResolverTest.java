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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.context.ITemplateContext;

import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class GraviteeMessageResolverTest {
    @Mock
    DynamicDictionaryProvider domainBasedDictionaryProvider;

    @Mock
    FileSystemDictionaryProvider dictionaryProvider;

    @Mock
    ITemplateContext context;

    private static final String MSG_WITHOUT_PARAM = "message-without-param";
    private static final String MSG_WITH_PARAM = "message-wit-param";
    private static final String MSG_WITH_MULTI_PARAM = "message-with-multi-param";

    private GraviteeMessageResolver messageResolver;

    @BeforeEach
    public void setUp() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(MSG_WITHOUT_PARAM, "hello world");
        properties.setProperty(MSG_WITH_PARAM, "hello user: {0}");
        /*
          apostrophe should be added twice in message properties. For more info check:
          https://stackoverflow.com/questions/4449639/apostrophe-doesnt-get-translated-properly-when-placed-in-a-resource-bundle
         */
        properties.setProperty(MSG_WITH_MULTI_PARAM, "Don''t have an account user: {0} ? Use temporary name: {1}.");

        messageResolver = new GraviteeMessageResolver(dictionaryProvider, domainBasedDictionaryProvider);

        when(context.getLocale()).thenReturn(Locale.ENGLISH);
        when(domainBasedDictionaryProvider.getDictionaryFor(any())).thenReturn(properties);
        when(dictionaryProvider.getDictionaryFor(any())).thenReturn(new Properties());
    }

    @Test
    public void shouldResolveMessage_withoutParameter() {
        String message = messageResolver.resolveMessage(context, null, MSG_WITHOUT_PARAM, null);

        assertEquals("hello world", message);
    }

    @Test
    public void shouldResolveMessage_with_One_Parameter_noParamValue() {
        String message = messageResolver.resolveMessage(context, null, MSG_WITH_PARAM, null);

        assertEquals("hello user: {0}", message);
    }

    @Test
    public void shouldResolveMessage_with_One_Parameter_withParamValue() {
        Object[] msgParams = {"Bob"};
        String message = messageResolver.resolveMessage(context, null, MSG_WITH_PARAM, msgParams);

        assertEquals("hello user: Bob", message);
    }

    @Test
    public void shouldResolveMessage_with_Multi_Parameter_withParamValue() {
        Object[] msgParams = {"Alice", "Bob"};
        String message = messageResolver.resolveMessage(context, null, MSG_WITH_MULTI_PARAM, msgParams);

        assertEquals("Don't have an account user: Alice ? Use temporary name: Bob.", message);
    }
}