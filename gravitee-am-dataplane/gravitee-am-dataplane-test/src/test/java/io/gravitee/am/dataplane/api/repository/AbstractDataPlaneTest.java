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
package io.gravitee.am.dataplane.api.repository;

import io.gravitee.am.dataplane.api.repository.test.DataPlaneTestConfigurationLoader;
import io.gravitee.am.dataplane.api.repository.test.DataPlaneTestInitializer;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataPlaneTestConfigurationLoader.class},
        loader = AnnotationConfigContextLoader.class)
public abstract class AbstractDataPlaneTest {

    @Autowired
    protected DataPlaneTestInitializer dataPlaneInitializer;

    @Before
    public void init() throws Exception {
        if (dataPlaneInitializer != null) {
            dataPlaneInitializer.before(this.getClass());
        }
    }

    @After
    public void clear() throws Exception {
        if (dataPlaneInitializer != null) {
            dataPlaneInitializer.after(this.getClass());
        }
    }
}
