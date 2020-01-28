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
import { Injectable } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { AppConfig } from "../../config/app.config";

@Injectable()
export class PlatformService {
  private platformURL = AppConfig.settings.baseURL + '/platform/';
  private domainsURL = AppConfig.settings.baseURL + '/domains/';
  private adminDomain = AppConfig.settings.authentication.domainId;

  constructor(private http: HttpClient) { }

  identities(): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/identities');
  }

  socialIdentities(): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/identities?external=true')
  }

  identitySchema(id): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/identities/' + id + '/schema');
  }

  identityProviders(): Observable<any> {
    return this.http.get<any>(this.platformURL + 'identities');
  }

  identityProvider(id): Observable<any> {
    return this.http.get<any>(this.platformURL + 'identities/' + id);
  }

  createIdentityProvider(idp) {
    return this.http.post<any>(this.platformURL + 'identities', idp);
  }

  updateIdentityProvider(id, idp) {
    return this.http.put<any>(this.platformURL + 'identities/' + id, {
      'name' : idp.name,
      'configuration' : idp.configuration,
      'mappers' : idp.mappers,
      'roleMapper' : idp.roleMapper
    });
  }

  deleteIdentityProvider(id) {
    return this.http.delete<any>(this.platformURL + 'identities/' + id);
  }

  certificates(): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/certificates');
  }

  certificateSchema(id): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/certificates/' + id + '/schema');
  }

  extensionGrants(): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/extensionGrants');
  }

  extensionGrantSchema(id): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/extensionGrants/' + id + '/schema');
  }

  reporterSchema(id): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/reporters/' + id + '/schema');
  }

  audits(page, size, type?, status?, user?, from?, to?): Observable<any> {
    return this.http.get(this.platformURL + 'audits?page=' + page + '&size=' + size +
      (type ? '&type=' + type : '') +
      (status ? '&status=' + status : '') +
      (user ? '&user=' + user : '') +
      (from ? '&from=' + from : '') +
      (to ? '&to=' + to : ''));
  }

  audit(auditId): Observable<any> {
    return this.http.get<any>(this.platformURL + 'audits/' + auditId);
  }

  auditEventTypes(): Observable<any> {
    return this.http.get<any>(this.platformURL + 'audits/events');
  }

  policies(): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/policies');
  }

  policySchema(id): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/policies/' + id + '/schema');
  }

  searchUsers(searchTerm, page, size): Observable<any> {
    return this.http.get<any>(this.platformURL + 'search/users?q=' + searchTerm + '&page=' + page + '&size=' + size);
  }

  roles(scope?): Observable<any> {
    return this.http.get<any>(this.platformURL + 'roles' + (scope ? '?scope=' + scope : ''));
  }

  role(roleId): Observable<any> {
    return this.http.get<any>(this.platformURL + 'roles/' + roleId);
  }

  createRole(role): Observable<any> {
    return this.http.post<any>(this.platformURL + 'roles', role);
  }

  updateRole(roleId, role): Observable<any> {
    return this.http.put<any>(this.platformURL + 'roles/' + roleId, {
      'name' : role.name,
      'description' : role.description,
      'permissions' : role.permissions
    });
  }

  deleteRole(roleId): Observable<any> {
    return this.http.delete<any>(this.platformURL + 'roles/' + roleId);
  }

  groups(page?: number, size?: number): Observable<any> {
    return this.http.get<any>(this.platformURL + 'groups' +
      (page !== undefined ? '?page=' + page : '') +
      (size !== undefined ? '&size=' + size : ''));
  }

  group(groupId) {
    return this.http.get<any>(this.platformURL + 'groups/' + groupId);
  }

  createGroup(group): Observable<any> {
    return this.http.post<any>(this.platformURL + 'groups', group);
  }

  updateGroup(groupId, group): Observable<any> {
    return this.http.put<any>(this.platformURL + 'groups/' + groupId, {
      'name' : group.name,
      'description' : group.description,
      'members' : group.members
    });
  }

  deleteGroup(groupId): Observable<any> {
    return this.http.delete<any>(this.platformURL + 'groups/' + groupId);
  }

  groupRoles(groupId): Observable<any>  {
    return this.http.get<any>(this.platformURL + 'groups/' + groupId + '/roles');
  }

  revokeGroupRole(groupId, roleId): Observable<any> {
    return this.http.delete<any>(this.platformURL + 'groups/' + groupId + '/roles/' + roleId);
  }

  assignGroupRoles(groupId, roles): Observable<any> {
    return this.http.post<any>(this.platformURL + 'groups/' + groupId + '/roles', roles);
  }

  groupMembers(groupId): Observable<any>  {
    return this.http.get<any>(this.platformURL + 'groups/' + groupId + '/members');
  }

  users(page, size): Observable<any>  {
    return this.http.get<any>(this.platformURL + 'users?page=' + page + '&size=' + size);
  }

  user(userId): Observable<any>  {
    return this.http.get<any>(this.platformURL + 'users/' + userId);
  }

  userRoles(userId): Observable<any>  {
    return this.http.get<any>(this.platformURL + 'users/' + userId + '/roles');
  }

  revokeUserRole(userId, roleId): Observable<any> {
    return this.http.delete<any>(this.platformURL + 'users/' + userId + '/roles/' + roleId);
  }

  assignUserRoles(userId, roles): Observable<any> {
    return this.http.post<any>(this.platformURL + 'users/' + userId + '/roles', roles);
  }

  reporters(): Observable<any>  {
    return this.http.get<any>(this.platformURL + 'reporters');
  }

  reporter(reporterId): Observable<any>  {
    return this.http.get<any>(this.platformURL + 'reporters/' + reporterId);
  }

  updateReporter(reporterId, reporter): Observable<any> {
    return this.http.put<any>(this.platformURL + 'reporters/' + reporterId, {
      'name' : reporter.name,
      'enabled': reporter.enabled,
      'configuration' : reporter.configuration
    });
  }

  settings(): Observable<any>  {
    return this.http.get<any>(this.platformURL + 'settings');
  }

  patchSettings(settings): Observable<any>  {
    return this.http.patch<any>(this.platformURL + 'settings', settings);
  }

  forms(template): Observable<any>  {
    return this.http.get<any>(this.platformURL + 'forms?template=' + template);
  }

  factors(): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/factors');
  }

  factorSchema(id): Observable<any> {
    return this.http.get<any>(this.platformURL + 'plugins/factors/' + id + '/schema');
  }
}
