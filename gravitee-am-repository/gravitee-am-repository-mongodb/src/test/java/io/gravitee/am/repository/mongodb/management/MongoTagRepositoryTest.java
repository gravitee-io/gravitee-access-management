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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.Tag;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.TagRepository;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import java.util.Set;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoTagRepositoryTest extends AbstractManagementRepositoryTest {

    public static final String ORGANIZATION_ID = "orga#1";

    @Autowired
    private TagRepository tagRepository;

    @Override
    public String collectionName() {
        return "tags";
    }

    @Test
    public void testFindAll() throws TechnicalException {
        // create tag
        Tag tag = new Tag();
        tag.setName("testName");
        tag.setDescription("Description");
        tag.setOrganizationId(ORGANIZATION_ID);
        tagRepository.create(tag).blockingGet();

        // fetch domains
        TestSubscriber<Tag> testObserver1 = tagRepository.findAll(ORGANIZATION_ID).test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValueCount(1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create tag
        Tag tag = new Tag();
        tag.setName("testName");
        Tag tagCreated = tagRepository.create(tag).blockingGet();

        // fetch domain
        TestObserver<Tag> testObserver = tagRepository.findById(tagCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(d -> d.getName().equals("testName"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        tagRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        // create tag
        Tag tag = new Tag();
        tag.setName("testName");

        TestObserver<Tag> testObserver = tagRepository.create(tag).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(tagCreated -> tagCreated.getName().equals(tag.getName()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create tag
        Tag tag = new Tag();
        tag.setName("testName");
        Tag tagCreated = tagRepository.create(tag).blockingGet();

        // update tag
        Tag updatedTag = new Tag();
        updatedTag.setId(tagCreated.getId());
        updatedTag.setName("testUpdatedName");

        TestObserver<Tag> testObserver = tagRepository.update(updatedTag).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(t -> t.getName().equals(updatedTag.getName()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create tag
        Tag tag = new Tag();
        tag.setName("testName");
        Tag tagCreated = tagRepository.create(tag).blockingGet();

        // fetch tag
        TestObserver<Tag> testObserver = tagRepository.findById(tagCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(t -> t.getName().equals(tag.getName()));

        // delete tag
        TestObserver testObserver1 = tagRepository.delete(tagCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch tag
        tagRepository.findById(tagCreated.getId()).test().assertEmpty();
    }
}
