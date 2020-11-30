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
package io.gravitee.am.repository.management;

import io.gravitee.am.repository.RepositoriesTestInitializer;
import io.gravitee.am.repository.management.test.config.ManagementTestConfigurationLoader;
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
@ContextConfiguration(classes = {ManagementTestConfigurationLoader.class},
        loader = AnnotationConfigContextLoader.class)
public abstract class AbstractManagementTest {

    @Autowired
    protected RepositoriesTestInitializer repositoriesInitializers;

    @Before
    public void init() throws Exception {
        if (repositoriesInitializers != null) {
            repositoriesInitializers.before(this.getClass());
        }
    }

    @After
    public void clear() throws Exception {
        if (repositoriesInitializers != null) {
            repositoriesInitializers.after(this.getClass());
        }
    }
}