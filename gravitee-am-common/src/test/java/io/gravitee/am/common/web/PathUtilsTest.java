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
package io.gravitee.am.common.web;

import io.gravitee.am.common.utils.PathUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PathUtilsTest {

    @Test
    public void sanitize() {

        assertEquals("/test", PathUtils.sanitize("/test"));
    }

    @Test
    public void sanitize_emptyPath() {

        assertEquals("/", PathUtils.sanitize(""));
    }

    @Test
    public void sanitize_nullPath() {

        assertEquals("/", PathUtils.sanitize(null));
    }

    @Test
    public void sanitize_multipleSlashesPath() {

        assertEquals("/test", PathUtils.sanitize("////test/////"));
    }

    @Test
    public void sanitize_slashPath() {

        assertEquals("/", PathUtils.sanitize("/"));
    }
}