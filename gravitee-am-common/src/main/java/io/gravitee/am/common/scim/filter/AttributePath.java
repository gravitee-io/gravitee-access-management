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
package io.gravitee.am.common.scim.filter;

import io.gravitee.am.common.scim.CommonAttribute;
import io.gravitee.am.common.scim.Schema;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *  attrPath  = [URI ":"] ATTRNAME *1subAttr
 *                    ; SCIM attribute name
 *                    ; URI is SCIM "schema" URI
 *
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.4.2.2">3.4.2.2. Filtering</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AttributePath {
    /**
     * A regular expression to match the components of an attribute path.
     * Given the path "urn:scim:schemas:core:user:1.0:name.familyName" then
     * group 2 will match "urn:scim:schemas:core:user:1.0", group 3 matches
     * "name" and group 5 matches "familyName".
     */
    private static final Pattern pattern = Pattern.compile("^((.+):)?([^.]+)(\\.(.+))?$");

    /**
     * The URI of the attribute schema.
     */
    private final String attributeSchema;

    /**
     * The name of the attribute.
     */
    private final String attributeName;

    /**
     * The name of the sub-attribute, or {@code null} if absent.
     */
    private final String subAttributeName;

    /**
     * Create a new attribute path.
     *
     * @param attributeSchema   The URI of the attribute schema.
     * @param attributeName     The name of the attribute.
     * @param subAttributeName  The name of the sub-attribute, or {@code null} if
     *                          absent.
     */
    public AttributePath(final String attributeSchema,
                         final String attributeName,
                         final String subAttributeName) {
        this.attributeSchema  = attributeSchema;
        this.attributeName    = attributeName;
        this.subAttributeName = subAttributeName;
    }

    /**
     * Parse an attribute path.
     *
     * @param path  The attribute path.
     * @param defaultSchema The default schema to assume for attributes that do
     *                      not have the schema part of the urn specified. The
     *                      'id', 'externalId', and 'meta' attributes will always
     *                      assume the SCIM Core schema.
     *
     * @return The parsed attribute path.
     */
    public static AttributePath parse(final String path,
                                      final String defaultSchema) {
        final Matcher matcher = pattern.matcher(path);

        if (!matcher.matches() || matcher.groupCount() != 5) {
            throw new IllegalArgumentException(
                    String.format(
                            "'%s' does not match '[schema:]attr[.sub-attr]' format", path));
        }

        final String attributeSchema = matcher.group(2);
        final String attributeName = matcher.group(3);
        final String subAttributeName = matcher.group(5);

        if (attributeSchema != null) {
            return new AttributePath(attributeSchema, attributeName,
                    subAttributeName);
        } else {
            if (attributeName.equalsIgnoreCase(
                    CommonAttribute.ID) ||
                    attributeName.equalsIgnoreCase(
                            CommonAttribute.EXTERNAL_ID) ||
                    attributeName.equalsIgnoreCase(
                            CommonAttribute.META)) {
                return new AttributePath(Schema.SCHEMA_URI_CORE, attributeName,
                        subAttributeName);
            } else {
                return new AttributePath(defaultSchema, attributeName,
                        subAttributeName);
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        toString(builder);
        return builder.toString();
    }

    private void toString(final StringBuilder builder) {
        if (!attributeSchema.equalsIgnoreCase(Schema.SCHEMA_URI_CORE)) {
            builder.append(attributeSchema);
            builder.append(':');
        }

        builder.append(attributeName);
        if (subAttributeName != null) {
            builder.append('.');
            builder.append(subAttributeName);
        }
    }

    public String getAttributeSchema()
    {
        return attributeSchema;
    }

    public String getAttributeName()
    {
        return attributeName;
    }

    public String getSubAttributeName()
    {
        return subAttributeName;
    }
}
