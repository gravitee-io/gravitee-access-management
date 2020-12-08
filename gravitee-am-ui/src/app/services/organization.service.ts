/*
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
import {Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Observable} from 'rxjs';
import {AppConfig} from '../../config/app.config';
import {map} from 'rxjs/operators';

@Injectable()
export class OrganizationService {
  private organizationURL = AppConfig.settings.organizationBaseURL;
  private platformURL = AppConfig.settings.baseURL + '/platform';

  constructor(private http: HttpClient) {
  }

  private computeIdentitiesParameters(external, expandIcon: boolean = false, expandDisplayName: boolean = false, expandLabels: boolean = false) {
    const params = [];
    if (external) {
      params.push('external=true');
    }
    if (expandIcon) {
      params.push('expand=icon');
    }
    if (expandDisplayName) {
      params.push('expand=displayName');
    }
    if (expandLabels) {
      params.push('expand=labels');
    }
    return params.length > 0 ? `?${params.join('&')}` : params;
  }

  identities(expandIcon: boolean = false, expandDisplayName: boolean = false, expandLabels: boolean = false): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/plugins/identities' + this.computeIdentitiesParameters(false, expandIcon, expandDisplayName, expandLabels));
  }

  socialIdentities(expandIcon: boolean = false, expandDisplayName: boolean = false, expandLabels: boolean = false): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/plugins/identities' + this.computeIdentitiesParameters(true, expandIcon, expandDisplayName, expandLabels));
  }

  identitySchema(id): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/plugins/identities/' + id + '/schema');
  }

  identityProviders(): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/identities');
  }

  identityProvider(id): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/identities/' + id);
  }

  createIdentityProvider(idp) {
    return this.http.post<any>(this.organizationURL + '/identities', idp);
  }

  updateIdentityProvider(id, idp) {
    return this.http.put<any>(this.organizationURL + '/identities/' + id, {
      'name': idp.name,
      'configuration': idp.configuration,
      'mappers': idp.mappers,
      'roleMapper': idp.roleMapper
    });
  }

  deleteIdentityProvider(id) {
    return this.http.delete<any>(this.organizationURL + '/identities/' + id);
  }

  certificates(): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/plugins/certificates');
  }

  certificateSchema(id): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/plugins/certificates/' + id + '/schema');
  }

  extensionGrants(): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/plugins/extensionGrants');
  }

  extensionGrantSchema(id): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/plugins/extensionGrants/' + id + '/schema');
  }

  reporterSchema(id): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/plugins/reporters/' + id + '/schema');
  }

  audits(page, size, type?, status?, user?, from?, to?): Observable<any> {
    return this.http.get(this.organizationURL + '/audits?page=' + page + '&size=' + size +
      (type ? '&type=' + type : '') +
      (status ? '&status=' + status : '') +
      (user ? '&user=' + user : '') +
      (from ? '&from=' + from : '') +
      (to ? '&to=' + to : ''));
  }

  audit(auditId): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/audits/' + auditId);
  }

  auditEventTypes(): Observable<any> {
    // Audit event type list is retrieved on platform level.
    return this.http.get<any>(this.platformURL + '/audits/events');
  }

  policies(expandSchema = false, expandIcon = false): Observable<any> {
    let url = `${this.organizationURL}/plugins/policies`;
    const expand = [];
    if (expandSchema) {
      expand.push('expand=schema');
    }
    if (expandIcon) {
      expand.push('expand=icon');
    }
    if (expand.length > 0) {
      url += `?${expand.join('&')}`;
    }
    return this.http.get<any>(url);
  }

  policySchema(id): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/plugins/policies/' + id + '/schema');
  }

  policyDocumentation(id): Observable<any> {
    const headers = new HttpHeaders().set('Content-Type', 'text/plain; charset=utf-8');
    return this.http.get<any>(this.organizationURL + '/plugins/policies/' + id + '/documentation', {
      headers,
      responseType: 'text' as 'json'
    });
  }

  searchUsers(searchTerm, page, size): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/users?' + searchTerm + '&page=' + page + '&size=' + size);
  }

  roles(type?): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/roles' + (type ? '?type=' + type : ''));
  }

  role(roleId): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/roles/' + roleId);
  }

  systemRole(roleId): Observable<any> {
    return this.http.get<any>(this.platformURL + '/roles/' + roleId);
  }

  createRole(role): Observable<any> {
    return this.http.post<any>(this.organizationURL + '/roles', role);
  }

  updateRole(roleId, role): Observable<any> {
    return this.http.put<any>(this.organizationURL + '/roles/' + roleId, {
      'name': role.name,
      'description': role.description,
      'permissions': role.permissions
    });
  }

  deleteRole(roleId): Observable<any> {
    return this.http.delete<any>(this.organizationURL + '/roles/' + roleId);
  }


  members(): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/members')
      .pipe(map(response => {
        const memberships = response.memberships;
        const metadata = response.metadata;
        const members = memberships.map(m => {
          m.roleName = (metadata['roles'][m.roleId]) ? metadata['roles'][m.roleId].name : 'Unknown role';
          if (m.memberType === 'user') {
            m.name = (metadata['users'][m.memberId]) ? metadata['users'][m.memberId].displayName : 'Unknown user';
          } else if (m.memberType === 'group') {
            m.name = (metadata['groups'][m.memberId]) ? metadata['groups'][m.memberId].displayName : 'Unknown group';
          }
          return m;
        });
        return members;
      }));
  }

  addMember(memberId, memberType, role) {
    return this.http.post<any>(this.organizationURL + '/members', {
      'memberId': memberId,
      'memberType': memberType,
      'role': role
    });
  }

  removeMember(membershipId) {
    return this.http.delete<any>(this.organizationURL + '/members/' + membershipId);
  }

  createForm(form): Observable<any> {
    return this.http.post<any>(this.organizationURL + '/forms/', form);
  }

  updateForm(id, form): Observable<any> {
    return this.http.put<any>(this.organizationURL + '/forms/' + id, {
      'enabled': form.enabled,
      'content': form.content
    });
  }

  deleteForm(id): Observable<any> {
    return this.http.delete<any>(this.organizationURL + '/forms/' + id);
  }

  groups(page?: number, size?: number): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/groups' +
      (page !== undefined ? '?page=' + page : '') +
      (size !== undefined ? '&size=' + size : ''));
  }

  group(groupId) {
    return this.http.get<any>(this.organizationURL + '/groups/' + groupId);
  }

  createGroup(group): Observable<any> {
    return this.http.post<any>(this.organizationURL + '/groups', group);
  }

  updateGroup(groupId, group): Observable<any> {
    return this.http.put<any>(this.organizationURL + '/groups/' + groupId, {
      'name': group.name,
      'description': group.description,
      'members': group.members
    });
  }

  deleteGroup(groupId): Observable<any> {
    return this.http.delete<any>(this.organizationURL + '/groups/' + groupId);
  }

  groupRoles(groupId): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/groups/' + groupId + '/roles');
  }

  revokeGroupRole(groupId, roleId): Observable<any> {
    return this.http.delete<any>(this.organizationURL + '/groups/' + groupId + '/roles/' + roleId);
  }

  assignGroupRoles(groupId, roles): Observable<any> {
    return this.http.post<any>(this.organizationURL + '/groups/' + groupId + '/roles', roles);
  }

  groupMembers(groupId): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/groups/' + groupId + '/members');
  }

  users(page, size): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/users?page=' + page + '&size=' + size);
  }

  user(userId): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/users/' + userId);
  }

  deleteUser(userId): Observable<any> {
    return this.http.delete<any>(this.organizationURL + '/users/' + userId);
  }

  userRoles(userId): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/users/' + userId + '/roles');
  }

  revokeUserRole(userId, roleId): Observable<any> {
    return this.http.delete<any>(this.organizationURL + '/users/' + userId + '/roles/' + roleId);
  }

  assignUserRoles(userId, roles): Observable<any> {
    return this.http.post<any>(this.organizationURL + '/users/' + userId + '/roles', roles);
  }

  reporters(): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/reporters');
  }

  reporter(reporterId): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/reporters/' + reporterId);
  }

  updateReporter(reporterId, reporter): Observable<any> {
    return this.http.put<any>(this.organizationURL + '/reporters/' + reporterId, {
      'name': reporter.name,
      'enabled': reporter.enabled,
      'configuration': reporter.configuration
    });
  }

  settings(): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/settings');
  }

  patchSettings(settings): Observable<any> {
    return this.http.patch<any>(this.organizationURL + '/settings', settings);
  }

  forms(template): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/forms?template=' + template);
  }

  factors(): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/plugins/factors');
  }

  factorSchema(id): Observable<any> {
    return this.http.get<any>(this.organizationURL + '/plugins/factors/' + id + '/schema');
  }

  flowSchema(): Observable<any> {
    return this.http.get<any>(this.platformURL + '/configuration/flow/schema');
  }
}
