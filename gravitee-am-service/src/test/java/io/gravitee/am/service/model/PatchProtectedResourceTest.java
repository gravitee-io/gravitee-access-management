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
import io.gravitee.am.model.permissions.Permission;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
        final var NEW_DESCRIPTION = "MyNewDescription";
        final var DESCRIPTION = "MyDescription";

        final var resource = new ProtectedResource();
        resource.setName("Old Name");
        resource.setDescription(DESCRIPTION);
        resource.setResourceIdentifiers(List.of("https://old.example.com"));

        final var patchResource = new PatchProtectedResource();
        patchResource.setDescription(Optional.of(NEW_DESCRIPTION));

        final var updatedResource = patchResource.patch(resource);
        assertEquals("Old Name", updatedResource.getName());
        assertEquals(NEW_DESCRIPTION, updatedResource.getDescription());
        assertEquals(List.of("https://old.example.com"), updatedResource.getResourceIdentifiers());
    }

    @Test
    public void patchResourceIdentifiersOnly() {
        final var NEW_RESOURCE_IDENTIFIERS = List.of("https://new.example.com");
        final var OLD_RESOURCE_IDENTIFIERS = List.of("https://old.example.com");

        final var resource = new ProtectedResource();
        resource.setName("Old Name");
        resource.setDescription("Old Description");
        resource.setResourceIdentifiers(OLD_RESOURCE_IDENTIFIERS);

        final var patchResource = new PatchProtectedResource();
        patchResource.setResourceIdentifiers(Optional.of(NEW_RESOURCE_IDENTIFIERS));

        final var updatedResource = patchResource.patch(resource);
        assertEquals("Old Name", updatedResource.getName());
        assertEquals("Old Description", updatedResource.getDescription());
        assertEquals(NEW_RESOURCE_IDENTIFIERS, updatedResource.getResourceIdentifiers());
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
        final var NEW_NAME = "MyNewName";
        final var NEW_DESCRIPTION = "MyNewDescription";
        final var NEW_RESOURCE_IDENTIFIERS = List.of("https://new.example.com");
        final var updateFeature = new UpdateProtectedResourceFeature();
        updateFeature.setKey("new_feature");
        updateFeature.setDescription("New Feature");

        final var resource = new ProtectedResource();
        resource.setName("Old Name");
        resource.setDescription("Old Description");
        resource.setResourceIdentifiers(List.of("https://old.example.com"));
        resource.setFeatures(Collections.emptyList());

        final var patchResource = new PatchProtectedResource();
        patchResource.setName(Optional.of(NEW_NAME));
        patchResource.setDescription(Optional.of(NEW_DESCRIPTION));
        patchResource.setResourceIdentifiers(Optional.of(NEW_RESOURCE_IDENTIFIERS));
        patchResource.setFeatures(Optional.of(List.of(updateFeature)));

        final var updatedResource = patchResource.patch(resource);
        assertEquals(NEW_NAME, updatedResource.getName());
        assertEquals(NEW_DESCRIPTION, updatedResource.getDescription());
        assertEquals(NEW_RESOURCE_IDENTIFIERS, updatedResource.getResourceIdentifiers());
        assertNotNull(updatedResource.getFeatures());
        assertEquals(1, updatedResource.getFeatures().size());
        assertEquals("new_feature", updatedResource.getFeatures().get(0).getKey());
    }

    @Test
    public void patchWithEmptyOptional_shouldSetToNull() {
        final var ORIGINAL_NAME = "OriginalName";
        final var ORIGINAL_DESCRIPTION = "OriginalDescription";
        final var ORIGINAL_RESOURCE_IDENTIFIERS = List.of("https://original.example.com");

        final var resource = new ProtectedResource();
        resource.setName(ORIGINAL_NAME);
        resource.setDescription(ORIGINAL_DESCRIPTION);
        resource.setResourceIdentifiers(ORIGINAL_RESOURCE_IDENTIFIERS);

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
        final var ORIGINAL_NAME = "OriginalName";
        final var ORIGINAL_DESCRIPTION = "OriginalDescription";
        final var ORIGINAL_RESOURCE_IDENTIFIERS = List.of("https://original.example.com");

        final var resource = new ProtectedResource();
        resource.setName(ORIGINAL_NAME);
        resource.setDescription(ORIGINAL_DESCRIPTION);
        resource.setResourceIdentifiers(ORIGINAL_RESOURCE_IDENTIFIERS);

        final var patchResource = new PatchProtectedResource();
        patchResource.setName(null);
        patchResource.setDescription(null);
        patchResource.setResourceIdentifiers(null);

        final var updatedResource = patchResource.patch(resource);
        assertEquals(ORIGINAL_NAME, updatedResource.getName());
        assertEquals(ORIGINAL_DESCRIPTION, updatedResource.getDescription());
        assertEquals(ORIGINAL_RESOURCE_IDENTIFIERS, updatedResource.getResourceIdentifiers());
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
    public void getRequiredPermissions_emptyPatch() {
        PatchProtectedResource patchResource = new PatchProtectedResource();
        assertEquals(Collections.emptySet(), patchResource.getRequiredPermissions());
    }

    @Test
    public void getRequiredPermissions_withName() {
        PatchProtectedResource patchResource = new PatchProtectedResource();
        patchResource.setName(Optional.of("patchName"));
        assertEquals(new HashSet<>(Arrays.asList(Permission.PROTECTED_RESOURCE)), patchResource.getRequiredPermissions());
    }

    @Test
    public void getRequiredPermissions_withDescription() {
        PatchProtectedResource patchResource = new PatchProtectedResource();
        patchResource.setDescription(Optional.of("patchDescription"));
        assertEquals(new HashSet<>(Arrays.asList(Permission.PROTECTED_RESOURCE)), patchResource.getRequiredPermissions());
    }

    @Test
    public void getRequiredPermissions_withResourceIdentifiers() {
        PatchProtectedResource patchResource = new PatchProtectedResource();
        patchResource.setResourceIdentifiers(Optional.of(List.of("https://example.com")));
        assertEquals(new HashSet<>(Arrays.asList(Permission.PROTECTED_RESOURCE)), patchResource.getRequiredPermissions());
    }

    @Test
    public void getRequiredPermissions_withFeatures() {
        PatchProtectedResource patchResource = new PatchProtectedResource();
        UpdateProtectedResourceFeature updateFeature = new UpdateProtectedResourceFeature();
        updateFeature.setKey("test_feature");
        updateFeature.setDescription("Test Feature");
        patchResource.setFeatures(Optional.of(List.of(updateFeature)));
        assertEquals(new HashSet<>(Arrays.asList(Permission.PROTECTED_RESOURCE)), patchResource.getRequiredPermissions());
    }

    @Test
    public void getRequiredPermissions_multipleFields() {
        PatchProtectedResource patchResource = new PatchProtectedResource();
        patchResource.setName(Optional.of("patchName"));
        patchResource.setDescription(Optional.of("patchDescription"));
        patchResource.setResourceIdentifiers(Optional.of(List.of("https://example.com")));
        UpdateProtectedResourceFeature updateFeature = new UpdateProtectedResourceFeature();
        updateFeature.setKey("test_feature");
        updateFeature.setDescription("Test Feature");
        patchResource.setFeatures(Optional.of(List.of(updateFeature)));

        assertEquals(new HashSet<>(Arrays.asList(Permission.PROTECTED_RESOURCE)), patchResource.getRequiredPermissions());
    }


}
