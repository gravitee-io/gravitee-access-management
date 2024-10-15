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


import io.gravitee.common.http.HttpMethod;
import lombok.Data;

import java.util.Map;

/**
 * Defines operations within a bulk job.  Each operation corresponds
 *       to a single HTTP request against a resource endpoint.  REQUIRED.
 *       The Operations attribute has the following sub-attributes:
 *
 * https://datatracker.ietf.org/doc/html/rfc7644#section-3.7
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
public class BulkOperation {
    /**
     * The HTTP method of the current operation.  Possible values
     *          are "POST", "PUT", "PATCH", or "DELETE".  REQUIRED.
     */
    private HttpMethod method;
    /**
     * The transient identifier of a newly created resource,
     *          unique within a bulk request and created by the client.  The
     *          bulkId serves as a surrogate resource id enabling clients to
     *          uniquely identify newly created resources in the response and
     *          cross-reference new resources in and across operations within a
     *          bulk request.  REQUIRED when "method" is "POST".
     */
    private String bulkId;
    /**
     * The current resource version.  Version MAY be used if the
     *          service provider supports entity-tags (ETags) (Section 2.3 of
     *          [RFC7232]) and "method" is "PUT", "PATCH", or "DELETE".
     */
    private String version;
    /**
     * The resource's relative path to the SCIM service provider's
     *          root.  If "method" is "POST", the value must specify a resource
     *          type endpoint, e.g., /Users or /Groups, whereas all other
     *          "method" values must specify the path to a specific resource,
     *          e.g., /Users/2819c223-7f76-453a-919d-413861904646.  REQUIRED in
     *          a request.
     */
    private String path;
    /**
     * The resource data as it would appear for a single SCIM POST,
     *          PUT, or PATCH operation.  REQUIRED in a request when "method"
     *          is "POST", "PUT", or "PATCH".
     */
    private Map<String, Object> data;
    /**
     * The resource endpoint URL.  REQUIRED in a response,
     *          except in the event of a POST failure.
     */
    private String location;
    /**
     *  The HTTP response body for the specified request
     *          operation.  When indicating a response with an HTTP status
     *          other than a 200-series response, the response body MUST be
     *          included.  For normal completion, the server MAY elect to omit
     *          the response body.
     */
    private Object response;
    /**
     * The HTTP response status code for the requested operation.
     *          When indicating an error, the "response" attribute MUST contain
     *          the detail error response as per Section 3.12.
     */
    private String status;

    public BulkOperation asResponse() {
        BulkOperation bulkOperation = new BulkOperation();
        bulkOperation.setMethod(this.getMethod());
        bulkOperation.setBulkId(this.getBulkId());
        bulkOperation.setLocation(this.getLocation());
        bulkOperation.setVersion(this.getVersion());
        bulkOperation.setResponse(this.getResponse());
        bulkOperation.setStatus(this.getStatus());
        // path and data are omitted for the response
        return bulkOperation;
    }
}
