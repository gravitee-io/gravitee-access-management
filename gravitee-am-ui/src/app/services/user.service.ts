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
import {AppConfig} from '../../config/app.config';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {OrganizationService} from './organization.service';
import {toHttpParams} from "../utils/http-utils";

@Injectable()
export class UserService {
  private usersURL = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient,
              private organizationService: OrganizationService) {
  }

  findByDomain(domainId, page, size): Observable<any> {
    return this.http.get<any>(this.usersURL + domainId + '/users?page=' + page + '&size=' + size);
  }

  get(domainId, id): Observable<any> {
    return this.http.get<any>(this.usersURL + domainId + '/users/' + id);
  }

  create(domainId, user): Observable<any> {
    return this.http.post<any>(this.usersURL + domainId + '/users', user);
  }

  update(domainId, id, user, organizationContext): Observable<any> {
    let updatedUserProfile = {
      'firstName': user.firstName,
      'lastName': user.lastName,
      'displayName': user.displayName,
      'email': user.email,
      'enabled': user.enabled,
      'client': user.client,
      'additionalInformation': user.additionalInformation
    };

    if (organizationContext) {
      return this.organizationService.updateUser(id, updatedUserProfile);
    }
    return this.http.put<any>(this.usersURL + domainId + '/users/' + id, updatedUserProfile);
  }

  updateStatus(domainId, id, status): Observable<any> {
    return this.http.put<any>(this.usersURL + domainId + '/users/' + id + '/status', {
      'enabled': status
    });
  }

  delete(domainId, id, organizationContext): Observable<any> {
    if (organizationContext) {
      return this.organizationService.deleteUser(id);
    }
    return this.http.delete<any>(this.usersURL + domainId + '/users/' + id);
  }

  resendRegistrationConfirmation(domainId, id): Observable<any> {
    return this.http.post<any>(this.usersURL + domainId + '/users/' + id + '/sendRegistrationConfirmation', {});
  }

  resetPassword(domainId, id, password, organizationContext): Observable<any> {
    if (organizationContext) {
      return this.organizationService.resetUserPassword(id, password);
    }
    return this.http.post<any>(this.usersURL + domainId + '/users/' + id + '/resetPassword', {
      'password': password
    });
  }

  unlock(domainId, id): Observable<any> {
    return this.http.post<any>(this.usersURL + domainId + '/users/' + id + '/unlock', {});
  }

  search(domainId, searchTerm, page, size, organizationContext): Observable<any> {
    if (organizationContext) {
      return this.organizationService.searchUsers(searchTerm, page, size);
    }
    return this.http.get<any>(this.usersURL + domainId + '/users?' + searchTerm + '&page=' + page + '&size=' + size);
  }

  consents(domainId, userId, clientId): Observable<any> {
    return this.http.get<any>(this.usersURL + domainId + '/users/' + userId + '/consents' + (clientId ? '?clientId=' + clientId : ''));
  }

  revokeConsents(domainId, userId, clientId): Observable<any> {
    return this.http.delete<any>(this.usersURL + domainId + '/users/' + userId + '/consents' + (clientId ? '?clientId=' + clientId : ''));
  }

  revokeConsent(domainId, userId, consentId): Observable<any> {
    return this.http.delete<any>(this.usersURL + domainId + '/users/' + userId + '/consents/' + consentId);
  }

  roles(domainId, userId, dynamic = false): Observable<any> {
    return this.http.get<any>(this.usersURL + domainId + '/users/' + userId + '/roles?dynamic=' + dynamic);
  }

  devices(domainId, userId): Observable<any> {
    return this.http.get<any>(this.usersURL + domainId + '/users/' + userId + '/devices');
  }

  revokeRole(domainId, userId, roleId, organizationContext): Observable<any> {
    if (organizationContext) {
      return this.organizationService.revokeUserRole(userId, roleId);
    }
    return this.http.delete<any>(this.usersURL + domainId + '/users/' + userId + '/roles/' + roleId);
  }

  assignRoles(domainId, userId, roles, organizationContext): Observable<any> {
    if (organizationContext) {
      return this.organizationService.assignUserRoles(userId, roles);
    }
    return this.http.post<any>(this.usersURL + domainId + '/users/' + userId + '/roles', roles);
  }

  factors(domainId, userId): Observable<any> {
    return this.http.get<any>(this.usersURL + domainId + '/users/' + userId + '/factors');
  }

  removeFactor(domainId, userId, factorId): Observable<any> {
    return this.http.delete<any>(this.usersURL + domainId + '/users/' + userId + '/factors/' + factorId);
  }

  credentials(domainId, userId): Observable<any> {
    return this.http.get<any>(this.usersURL + domainId + '/users/' + userId + '/credentials');
  }

  credential(domainId, userId, credentialId): Observable<any> {
    return this.http.get<any>(this.usersURL + domainId + '/users/' + userId + '/credentials/' + credentialId);
  }

  removeCredential(domainId, userId, credentialId): Observable<any> {
    return this.http.delete<any>(this.usersURL + domainId + '/users/' + userId + '/credentials/' + credentialId);
  }

  removeDevice(domainId, userId, deviceId): Observable<any> {
    return this.http.delete<any>(this.usersURL + domainId + '/users/' + userId + '/devices/' + deviceId);
  }

  audits(domainId, userId, page: number, size: number, type: string, status: string, from: number, to: number): Observable<any> {
    return this.http.get<any>(this.usersURL + domainId + '/users/' + userId + '/audits', {
      params: toHttpParams({
        page,
        size,
        type,
        status,
        from,
        to
      })
    });
  }

  audit(domainId, userId, auditId): Observable<any> {
    return this.http.get<any>(this.usersURL + domainId + '/users/' + userId + '/audits/' + auditId);
  }
}
