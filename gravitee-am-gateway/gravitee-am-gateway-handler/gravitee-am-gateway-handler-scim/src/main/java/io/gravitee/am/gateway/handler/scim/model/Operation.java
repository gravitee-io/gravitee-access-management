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
package io.gravitee.am.gateway.handler.scim.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.common.scim.filter.Filter;
import io.gravitee.am.common.scim.filter.Operator;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.5.2">3.5.2. Modifying with PATCH</a>
 *
 * The body of an HTTP PATCH request MUST contain the attribute
 *    "Operations", whose value is an array of one or more PATCH
 *    operations.  Each PATCH operation object MUST have exactly one "op"
 *    member, whose value indicates the operation to perform and MAY be one
 *    of "add", "remove", or "replace".
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "op")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Operation.AddOperation.class,
                name="add"),
        @JsonSubTypes.Type(value = Operation.AddOperation.class,
                name="Add"),
        @JsonSubTypes.Type(value = Operation.RemoveOperation.class,
                name="remove"),
        @JsonSubTypes.Type(value = Operation.RemoveOperation.class,
                name="Remove"),
        @JsonSubTypes.Type(value = Operation.ReplaceOperation.class,
                name="replace"),
        @JsonSubTypes.Type(value = Operation.ReplaceOperation.class,
                name="Replace")})
public abstract class Operation {

    private static final String SPLIT_PATH_PATTERN = "(?!\\d)\\.(?!\\d)";
    private static final String SQUARE_BRACKETS_PATTERN = "\\[(.*?)\\]";
    private static final Pattern PATH_FILTER_PATTERN = Pattern.compile(SQUARE_BRACKETS_PATTERN);
    private final Path path;

    Operation(final String path) {
        if (path == null) {
            this.path = new Path();
        } else {
            Matcher matcher = PATH_FILTER_PATTERN.matcher(path);
            String filter = matcher.find() ? matcher.group(1) : null;
            String[] paths = path.replaceAll(SQUARE_BRACKETS_PATTERN, "").split(SPLIT_PATH_PATTERN);
            this.path = new Path(paths[0], paths.length > 1 ? paths[1] : null, filter);
        }
    }

    public Path getPath() {
        return path;
    }

    public abstract void apply(final ObjectNode node);

    /**
     * The "add" operation is used to add a new attribute value to an
     *    existing resource.
     *
     * See <a href="https://tools.ietf.org/html/rfc7644#section-3.5.2.1">3.5.2.1.  Add Operation</a>
     */
    static final class AddOperation extends Operation {

        @JsonProperty
        private final JsonNode value;

        @JsonCreator
        private AddOperation(@JsonProperty(value = "path") final String path,
                             @JsonProperty(value = "value", required = true) final JsonNode value) {
            super(path);
            this.value = value;
        }

        /**
         * The result of the add operation depends upon what the target location
         *    indicated by "path" references:
         *
         *    -  If omitted, the target location is assumed to be the resource
         *       itself.  The "value" parameter contains a set of attributes to be
         *       added to the resource.
         *
         *    -  If the target location does not exist, the attribute and value are
         *       added.
         *
         *    -  If the target location specifies a complex attribute, a set of
         *       sub-attributes SHALL be specified in the "value" parameter.
         *
         *    -  If the target location specifies a multi-valued attribute, a new
         *       value is added to the attribute.
         *
         *    -  If the target location specifies a single-valued attribute, the
         *       existing value is replaced.
         *
         *    -  If the target location specifies an attribute that does not exist
         *       (has no value), the attribute is added with the new value.
         *
         *    -  If the target location exists, the value is replaced.
         *
         *    -  If the target location already contains the value specified, no
         *       changes SHOULD be made to the resource, and a success response
         *       SHOULD be returned.  Unless other operations change the resource,
         *       this operation SHALL NOT change the modify timestamp of the
         *       resource.
         *
         * @param node the resource.
         */
        @Override
        public void apply(ObjectNode node) {
            // If path is omitted, the target location is assumed to be the resource itself
            if (getPath().getAttributePath() == null) {
                if (value.isObject()) {
                    value.fieldNames().forEachRemaining(fieldName -> {
                        // If the target location does not exist, the attribute and value are added.
                        // If the target location exists, the value is replaced.
                        if (node.get(fieldName) == null || !value.get(fieldName).isArray()) {
                            node.set(fieldName, value.get(fieldName));
                        } else {
                            // If the target location specifies a multi-valued attribute, a new value is added to the attribute.
                            value.get(fieldName).forEach(valueNode -> ((ArrayNode) node.get(fieldName)).add(valueNode));
                        }
                    });
                }
                return;
            }

            JsonNode parentNode = node.get(getPath().getAttributePath());
            if (getPath().getSubAttribute() != null) {
                if (parentNode != null) {
                    ((ObjectNode) parentNode).set(getPath().getSubAttribute(), value);
                } else {
                    node.putObject(getPath().getAttributePath()).set(getPath().getSubAttribute(), value);
                }
            } else if (parentNode == null) {
                node.set(getPath().getAttributePath(), value);
            } else if (parentNode.isArray()) {
                if (value.isArray()) {
                    value.forEach(n -> ((ArrayNode) parentNode).add(n));
                } else if (value.isObject()) {
                    ((ArrayNode) parentNode).add(value);
                }
            } else {
                if (value.isObject()) {
                    value.fieldNames().forEachRemaining(fieldName -> ((ObjectNode) parentNode).set(fieldName, value.get(fieldName)));
                } else {
                    node.set(getPath().getAttributePath(), value);
                }
            }
        }
    }

    /**
     * The "remove" operation removes the value at the target location
     *    specified by the required attribute "path".
     *
     * See <a href="https://tools.ietf.org/html/rfc7644#section-3.5.2.2">3.5.2.2.  Remove Operation</a>
     */
    static final class RemoveOperation extends Operation {

        @JsonCreator
        private RemoveOperation(@JsonProperty(value = "path", required = true) final String path) {
            super(path);
        }

        /**
         * The operation performs
         *   the following functions, depending on the target location specified
         *    by "path":
         *
         *    -  If "path" is unspecified, the operation fails with HTTP status
         *       code 400 and a "scimType" error code of "noTarget".
         *
         *    -  If the target location is a single-value attribute, the attribute
         *       and its associated value is removed, and the attribute SHALL be
         *       considered unassigned.
         *
         *    -  If the target location is a multi-valued attribute and no filter
         *       is specified, the attribute and all values are removed, and the
         *       attribute SHALL be considered unassigned.
         *
         *    -  If the target location is a multi-valued attribute and a complex
         *       filter is specified comparing a "value", the values matched by the
         *       filter are removed.  If no other values remain after removal of
         *       the selected values, the multi-valued attribute SHALL be
         *       considered unassigned.
         *
         *    -  If the target location is a complex multi-valued attribute and a
         *       complex filter is specified based on the attribute's
         *       sub-attributes, the matching records are removed.  Sub-attributes
         *       whose values have been removed SHALL be considered unassigned.  If
         *       the complex multi-valued attribute has no remaining records, the
         *       attribute SHALL be considered unassigned.
         *
         * @param node the resource.
         */
        @Override
        public void apply(ObjectNode node) {
            // if there is no filter, just remove the value
            JsonNode parentNode = node.get(getPath().getAttributePath());
            if (getPath().getValuePath() == null) {
                if (getPath().getSubAttribute() == null) {
                    node.remove(getPath().getAttributePath());
                } else {
                    if (parentNode != null) {
                        ((ObjectNode) parentNode).remove(getPath().getSubAttribute());
                    }
                }
                return;
            }

            // apply filtering and remove the values
            // to use filter we assume that the current node must be an array (e.g emails[type eq "work"])
            if (parentNode != null && parentNode.isArray()) {
                ArrayNode arrayNode = (ArrayNode) parentNode;
                // get indices to delete
                List<Integer> indices = new LinkedList();
                for (int i = 0; i < arrayNode.size(); i++) {
                    JsonNode n = arrayNode.get(i);
                    if (filterMatch(n, getPath().getValuePath())) {
                        indices.add(i);
                    }
                }
                // remove elements
                Collections.reverse(indices);
                indices.forEach(i -> {
                    if (getPath().getSubAttribute() == null) {
                        arrayNode.remove(i);
                    } else {
                        ((ObjectNode) arrayNode.get(i)).remove(getPath().getSubAttribute());
                    }
                });
            }
        }
    }

    /**
     * The "replace" operation replaces the value at the target location
     *    specified by the "path".
     *
     * See <a href="https://tools.ietf.org/html/rfc7644#section-3.5.2.3">3.5.2.3.  Replace Operation</a>
     */
    static final class ReplaceOperation extends Operation {

        @JsonProperty
        private final JsonNode value;

        @JsonCreator
        private ReplaceOperation(@JsonProperty(value = "path") final String path,
                                 @JsonProperty(value = "value", required = true) final JsonNode value) {
            super(path);
            this.value = value;
        }

        /**
         * The operation performs the following
         *    functions, depending on the target location specified by "path":
         *
         *    -  If the "path" parameter is omitted, the target is assumed to be
         *       the resource itself.  In this case, the "value" attribute SHALL
         *       contain a list of one or more attributes that are to be replaced.
         *
         *    -  If the target location is a single-value attribute, the attributes
         *       value is replaced.
         *
         *    -  If the target location is a multi-valued attribute and no filter
         *       is specified, the attribute and all values are replaced.
         *
         *    -  If the target location path specifies an attribute that does not
         *       exist, the service provider SHALL treat the operation as an "add".
         *
         *    -  If the target location specifies a complex attribute, a set of
         *       sub-attributes SHALL be specified in the "value" parameter, which
         *       replaces any existing values or adds where an attribute did not
         *       previously exist.  Sub-attributes that are not specified in the
         *       "value" parameter are left unchanged.
         *
         *    -  If the target location is a multi-valued attribute and a value
         *       selection ("valuePath") filter is specified that matches one or
         *       more values of the multi-valued attribute, then all matching
         *       record values SHALL be replaced.
         *
         *    -  If the target location is a complex multi-valued attribute with a
         *       value selection filter ("valuePath") and a specific sub-attribute
         *       (e.g., "addresses[type eq "work"].streetAddress"), the matching
         *       sub-attribute of all matching records is replaced.
         *
         *    -  If the target location is a multi-valued attribute for which a
         *       value selection filter ("valuePath") has been supplied and no
         *       record match was made, the service provider SHALL indicate failure
         *       by returning HTTP status code 400 and a "scimType" error code of
         *       "noTarget".
         *
         * @param node the resource.
         */
        @Override
        public void apply(ObjectNode node) {
            // If the "path" parameter is omitted, the target is assumed to be the resource itself.
            if (getPath().getAttributePath() == null) {
                if (value.isObject()) {
                    value.fieldNames().forEachRemaining(fieldName -> node.replace(fieldName, value.get(fieldName)));
                }
                return;
            }

            // if there is no filter, just replace the value
            if (getPath().getValuePath() == null) {
                if (getPath().getSubAttribute() == null) {
                    node.replace(getPath().getAttributePath(), value);
                } else {
                    ((ObjectNode) node.get(getPath().getAttributePath())).replace(getPath().getSubAttribute(), value);
                }
                return;
            }

            // apply filtering and replace the values
            // to use filter we assume that the current node must be an array (e.g emails[type eq "work"])
            if (node.get(getPath().getAttributePath()).isArray()) {
                Iterator<JsonNode> it = node.get(getPath().getAttributePath()).deepCopy().iterator();
                int i = 0;
                while(it.hasNext()) {
                    JsonNode n = it.next();
                    if (filterMatch(n, getPath().getValuePath())) {
                        if (getPath().getSubAttribute() == null) {
                            ((ArrayNode) node.get(getPath().getAttributePath())).remove(i);
                            ((ArrayNode) node.get(getPath().getAttributePath())).insert(i, value);
                        } else {
                            ((ObjectNode) node.get(getPath().getAttributePath()).get(i)).replace(getPath().getSubAttribute(), value);
                        }
                    }
                    i++;
                }
            }
        }
    }

    static boolean filterMatch(JsonNode n, Filter filter) {
        // filtering requires an complex attribute
        if (!n.isObject()) {
            return false;
        }
        // no filter, continue
        if (filter == null) {
            return false;
        }
        // no filter attribute, continue
        if (filter.getFilterAttribute() == null) {
            return false;
        }
        // no filter attribute field to check, continue
        if (filter.getFilterAttribute().getAttributeName() == null) {
            return false;
        }
        // no filter operator to check, continue
        if (filter.getOperator() == null) {
            return false;
        }

        // process filtering
        final String filterValue = filter.getFilterValue();
        final Operator operator = filter.getOperator();
        final JsonNode attribute = n.get(filter.getFilterAttribute().getAttributeName());

        // no node to check, continue
        if (attribute == null) {
            return false;
        }

        switch (operator) {
            case EQUALITY:
                return filterValue != null && attribute.asText().equals(filterValue);
            case STARTS_WITH:
                return filterValue != null && attribute.asText().startsWith(filterValue);
            case ENDS_WITH:
                return filterValue != null && attribute.asText().endsWith(filterValue);
            case CONTAINS:
                return filterValue != null && attribute.asText().contains(filterValue);
            default:
                return false;
        }
    }
}
