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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FreemarkerMessageResolverTest {

    private DictionaryProvider directoryProvider = new FileSystemDictionaryProvider("src/test/resources/i18n_default");

    private FreemarkerMessageResolver cut;

    @Before
    public void init() {
        this.cut = new FreemarkerMessageResolver(directoryProvider.getDictionaryFor(new Locale("en")));
    }

    @Test
    public void shouldTranslateMessage() throws Exception {
        final var message = this.cut.exec(List.of("key.multi.lines"));
        Assert.assertNotNull(message);
        Assert.assertTrue(message instanceof String && ((String) message).contains("lines"));
    }

    @Test
    public void shouldTranslateMessage_WithParam() throws Exception {
        final var message = this.cut.exec(List.of("key.param", "myparam"));
        Assert.assertNotNull(message);
        Assert.assertTrue(message instanceof String && ((String) message).equals("value-en myparam"));
    }

    @Test
    public void shouldNoTranslateUnkownMessage() throws Exception {
        final var message = this.cut.exec(List.of("key.unknown"));
        Assert.assertNotNull(message);
        Assert.assertTrue(message instanceof String && ((String) message).equals("key.unknown"));
    }

}
