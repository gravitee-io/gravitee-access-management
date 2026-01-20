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
package io.gravitee.am.service.model;

import io.gravitee.am.model.ProtectedResource;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author GraviteeSource Team
 */
public class PatchProtectedResourceTest {

    @Test
    public void patchNameOnly() {
        final var NEW_NAME = "MyNewName";
        final var NAME = "MyName";

        final var resource = new ProtectedResource();
        resource.setName(NAME);
        resource.setDescription("Old Description");
        resource.setResourceIdentifiers(List.of("https://old.example.com"));

        final var patchResource = new PatchProtectedResource();
        patchResource.setName(Optional.of(NEW_NAME));

        final var updatedResource = patchResource.patch(resource);
        assertEquals(NEW_NAME, updatedResource.getName());
        assertEquals("Old Description", updatedResource.getDescription());
        assertEquals(List.of("https://old.example.com"), updatedResource.getResourceIdentifiers());
    }

    @Test
    public void patchDescriptionOnly() {
        final var newDescription = "MyNewDescription";
        final var description = "MyDescription";

        final var resource = new ProtectedResource();
        resource.setName("Old Name");
        resource.setDescription(description);
        resource.setResourceIdentifiers(List.of("https://old.example.com"));

        final var patchResource = new PatchProtectedResource();
        patchResource.setDescription(Optional.of(newDescription));

        final var updatedResource = patchResource.patch(resource);
        assertEquals("Old Name", updatedResource.getName());
        assertEquals(newDescription, updatedResource.getDescription());
        assertEquals(List.of("https://old.example.com"), updatedResource.getResourceIdentifiers());
    }

    @Test
    public void patchResourceIdentifiersOnly() {
        final var newResourceIdentifiersOnly = List.of("https://new.example.com");
        final var oldResourceIdentifiersOnly = List.of("https://old.example.com");

        final var resource = new ProtectedResource();
        resource.setName("Old Name");
        resource.setDescription("Old Description");
        resource.setResourceIdentifiers(oldResourceIdentifiersOnly);

        final var patchResource = new PatchProtectedResource();
        patchResource.setResourceIdentifiers(Optional.of(newResourceIdentifiersOnly));

        final var updatedResource = patchResource.patch(resource);
        assertEquals("Old Name", updatedResource.getName());
        assertEquals("Old Description", updatedResource.getDescription());
        assertEquals(newResourceIdentifiersOnly, updatedResource.getResourceIdentifiers());
    }

    @Test
    public void patchFeaturesOnly() {
        final var updateFeature = new UpdateProtectedResourceFeature();
        updateFeature.setKey("test_feature");
        updateFeature.setDescription("Test Feature");

        final var resource = new ProtectedResource();
        resource.setName("Old Name");
        resource.setDescription("Old Description");
        resource.setResourceIdentifiers(List.of("https://old.example.com"));
        resource.setFeatures(Collections.emptyList());

        final var patchResource = new PatchProtectedResource();
        patchResource.setFeatures(Optional.of(List.of(updateFeature)));

        final var updatedResource = patchResource.patch(resource);
        assertEquals("Old Name", updatedResource.getName());
        assertEquals("Old Description", updatedResource.getDescription());
        assertEquals(List.of("https://old.example.com"), updatedResource.getResourceIdentifiers());
        assertNotNull(updatedResource.getFeatures());
        assertEquals(1, updatedResource.getFeatures().size());
        assertEquals("test_feature", updatedResource.getFeatures().get(0).getKey());
    }

    @Test
    public void patchAllFields() {
        final var newName = "MyNewName";
        final var newDescription2 = "MyNewDescription";
        final var newCertificate = "NewCertficate";
        final var newResourceIdentifiers = List.of("https://new.example.com");
        final var updateFeature = new UpdateProtectedResourceFeature();
        updateFeature.setKey("new_feature");
        updateFeature.setDescription("New Feature");

        final var resource = new ProtectedResource();
        resource.setName("Old Name");
        resource.setDescription("Old Description");
        resource.setCertificate("Old Certificate");
        resource.setResourceIdentifiers(List.of("https://old.example.com"));
        resource.setFeatures(Collections.emptyList());

        final var patchResource = new PatchProtectedResource();
        patchResource.setName(Optional.of(newName));
        patchResource.setDescription(Optional.of(newDescription2));
        patchResource.setResourceIdentifiers(Optional.of(newResourceIdentifiers));
        patchResource.setFeatures(Optional.of(List.of(updateFeature)));
        patchResource.setCertificate(Optional.of(newCertificate));

        final var updatedResource = patchResource.patch(resource);
        assertEquals(newName, updatedResource.getName());
        assertEquals(newDescription2, updatedResource.getDescription());
        assertEquals(newCertificate, updatedResource.getCertificate());
        assertEquals(newResourceIdentifiers, updatedResource.getResourceIdentifiers());
        assertNotNull(updatedResource.getFeatures());
        assertEquals(1, updatedResource.getFeatures().size());
        assertEquals("new_feature", updatedResource.getFeatures().get(0).getKey());
    }

    @Test
    public void patchWithEmptyOptional_shouldSetToNull() {
        final var originalName = "OriginalName";
        final var originalDescription = "OriginalDescription";
        final var originalResourceIdentifiers = List.of("https://original.example.com");

        final var resource = new ProtectedResource();
        resource.setName(originalName);
        resource.setDescription(originalDescription);
        resource.setResourceIdentifiers(originalResourceIdentifiers);

        final var patchResource = new PatchProtectedResource();
        patchResource.setName(Optional.empty());
        patchResource.setDescription(Optional.empty());
        patchResource.setResourceIdentifiers(Optional.empty());

        final var updatedResource = patchResource.patch(resource);
        assertNull("name should have been set to null", updatedResource.getName());
        assertNull("description should have been set to null", updatedResource.getDescription());
        assertNull("resourceIdentifiers should have been set to null", updatedResource.getResourceIdentifiers());
    }

    @Test
    public void patchWithNullOptional_shouldNotUpdate() {
        final var originalName2 = "OriginalName";
        final var originalDescription2 = "OriginalDescription";
        final var originalResourceIdentifiers2 = List.of("https://original.example.com");

        final var resource = new ProtectedResource();
        resource.setName(originalName2);
        resource.setDescription(originalDescription2);
        resource.setResourceIdentifiers(originalResourceIdentifiers2);

        final var patchResource = new PatchProtectedResource();
        patchResource.setName(null);
        patchResource.setDescription(null);
        patchResource.setResourceIdentifiers(null);

        final var updatedResource = patchResource.patch(resource);
        assertEquals(originalName2, updatedResource.getName());
        assertEquals(originalDescription2, updatedResource.getDescription());
        assertEquals(originalResourceIdentifiers2, updatedResource.getResourceIdentifiers());
    }

    @Test
    public void patchWithEmptyList_shouldUpdate() {
        final var resource = new ProtectedResource();
        resource.setResourceIdentifiers(List.of("https://old.example.com"));

        final var patchResource = new PatchProtectedResource();
        patchResource.setResourceIdentifiers(Optional.of(Collections.emptyList()));

        final var updatedResource = patchResource.patch(resource);
        assertNotNull(updatedResource.getResourceIdentifiers());
        assertEquals(0, updatedResource.getResourceIdentifiers().size());
    }

    @Test
    public void hasAnyField_emptyPatch_returnsFalse() {
        final var patch = new PatchProtectedResource();
        assertEquals(false, patch.hasAnyField());
    }

    @Test
    public void hasAnyField_withAnyField_returnsTrue() {
        // name only
        var patch = new PatchProtectedResource();
        patch.setName(Optional.of("n"));
        assertEquals(true, patch.hasAnyField());

        // description only
        patch = new PatchProtectedResource();
        patch.setDescription(Optional.of("d"));
        assertEquals(true, patch.hasAnyField());

        // resourceIdentifiers only
        patch = new PatchProtectedResource();
        patch.setResourceIdentifiers(Optional.of(List.of("https://example.com")));
        assertEquals(true, patch.hasAnyField());

        // features only
        patch = new PatchProtectedResource();
        final var updateFeature = new UpdateProtectedResourceFeature();
        updateFeature.setKey("k");
        updateFeature.setDescription("desc");
        patch.setFeatures(Optional.of(List.of(updateFeature)));
        assertEquals(true, patch.hasAnyField());
    }
}
