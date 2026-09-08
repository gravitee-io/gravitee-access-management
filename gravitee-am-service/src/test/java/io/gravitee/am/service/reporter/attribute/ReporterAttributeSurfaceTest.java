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
package io.gravitee.am.service.reporter.attribute;

import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.UserProperties;
import org.junit.jupiter.api.Test;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the surface a mapping expression can address.
 *
 * @author GraviteeSource Team
 */
class ReporterAttributeSurfaceTest {

    private static final SensitiveAttributeDenylist DENYLIST = new SensitiveAttributeDenylist();

    private record ContextRoot(String key, Class<?> type) {
    }

    private static Stream<ContextRoot> roots() {
        return Stream.of(new ContextRoot("user", UserProperties.class), new ContextRoot("client", ClientProperties.class));
    }

    private static List<PropertyDescriptor> readableProperties(Class<?> type) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(type, Object.class);
            return Arrays.stream(beanInfo.getPropertyDescriptors())
                    .filter(property -> property.getReadMethod() != null)
                    .toList();
        } catch (IntrospectionException e) {
            throw new IllegalStateException("Unable to introspect " + type, e);
        }
    }

    private static boolean isStructure(PropertyDescriptor property) {
        Class<?> type = property.getPropertyType();
        return Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type);
    }

    @Test
    void theSurfaceIsActuallyWalked() {
        assertThat(readableProperties(UserProperties.class)).hasSizeGreaterThan(20);
        assertThat(readableProperties(ClientProperties.class)).hasSizeGreaterThan(5);
        assertThat(readableProperties(UserProperties.class).stream().filter(ReporterAttributeSurfaceTest::isStructure))
                .hasSizeGreaterThan(5);
    }

    @Test
    void noProjectionExposesADeniedName() {
        roots().forEach(root -> readableProperties(root.type()).forEach(property ->
                assertThat(DENYLIST.isDenied(property.getName()))
                        .withFailMessage("%s.%s is reachable from a reporter attribute mapping but its name is denied",
                                root.type().getSimpleName(), property.getName())
                        .isFalse()));
    }
}
