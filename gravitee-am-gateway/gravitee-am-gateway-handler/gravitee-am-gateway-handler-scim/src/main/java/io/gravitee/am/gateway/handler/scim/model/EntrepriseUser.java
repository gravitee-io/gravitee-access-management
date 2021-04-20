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

import java.util.Arrays;
import java.util.List;

/**
 * SCIM Enterprise User Resource
 *
 * See <a href="https://tools.ietf.org/html/rfc7643#section-4.3">4.3. Enterprise User Schema Extension</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EntrepriseUser extends User {

    public static final List<String> SCHEMAS = Arrays.asList(
        "urn:ietf:params:scim:schemas:core:2.0:User",
        "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
    );

    /**
     * A string identifier, typically numeric or alphanumeric, assigned
     *       to a person, typically based on order of hire or association with
     *       an organization.
     */
    private String employeeNumber;

    /**
     * Identifies the name of a cost center.
     */
    private String costCenter;

    /**
     * Identifies the name of an organization.
     */
    private String organization;

    /**
     * Identifies the name of a division.
     */
    private String division;

    /**
     * Identifies the name of a department.
     */
    private String department;

    /**
     * The user's manager.  A complex type that optionally allows service
     *       providers to represent organizational hierarchy by referencing the
     *       "id" attribute of another User.
     *
     *       value  The "id" of the SCIM resource representing the user's
     *          manager.  RECOMMENDED.
     *
     *       $ref  The URI of the SCIM resource representing the User's
     *          manager.  RECOMMENDED.
     *
     *       displayName  The displayName of the user's manager.  This
     *          attribute is OPTIONAL, and mutability is "readOnly".
     */
    private String manager;

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getCostCenter() {
        return costCenter;
    }

    public void setCostCenter(String costCenter) {
        this.costCenter = costCenter;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getDivision() {
        return division;
    }

    public void setDivision(String division) {
        this.division = division;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getManager() {
        return manager;
    }

    public void setManager(String manager) {
        this.manager = manager;
    }
}
