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
package io.gravitee.am.model.jose;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.fail;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class KeyTypeTest {

    @Test
    public void parse() {
        Assert.assertTrue("Unable to parse RSA key", KeyType.parse("RSA").getName()!=null);
        Assert.assertTrue("Unable to parse EC key", KeyType.parse("EC").getName()!=null);
        Assert.assertTrue("Unable to parse OCT key", KeyType.parse("oct").getName()!=null);
        Assert.assertTrue("Unable to parse OKP key", KeyType.parse("OKP").getName()!=null);
    }

    @Test(expected = NullPointerException.class)
    public void parse_null() {
        KeyType.parse(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parse_exception() {
        KeyType.parse("unknown");
    }
}
