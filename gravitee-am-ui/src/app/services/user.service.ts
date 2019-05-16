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
import {AppConfig} from "../../config/app.config";
import {Http, Response} from "@angular/http";
import {Observable} from "rxjs/Observable";

@Injectable()
export class UserService {
  private usersURL = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: Http) { }

  findByDomain(domainId, page, size): Observable<Response>  {
    return this.http.get(this.usersURL + domainId + "/users?page=" + page + "&size=" + size);
  }

  get(domainId, id): Observable<Response>  {
    return this.http.get(this.usersURL + domainId + "/users/" + id);
  }

  create(domainId, user): Observable<Response>  {
    return this.http.post(this.usersURL + domainId + "/users", user);
  }

  update(domainId, id, user): Observable<Response>  {
    return this.http.put(this.usersURL + domainId + "/users/" + id, {
      'firstName' : user.firstName,
      'lastName' : user.lastName,
      'email' : user.email,
      'enabled': user.enabled,
      'client' : user.client,
      'additionalInformation' : user.additionalInformation
    });
  }

  delete(domainId, id): Observable<Response>  {
    return this.http.delete(this.usersURL + domainId + "/users/" + id);
  }

  resendRegistrationConfirmation(domainId, id) {
    return this.http.post(this.usersURL + domainId + "/users/" + id + "/sendRegistrationConfirmation", {});
  }

  resetPassword(domainId, id, password) {
    return this.http.post(this.usersURL + domainId + "/users/" + id + "/resetPassword", {
      'password': password
    });
  }

  unlock(domainId, id) {
    return this.http.post(this.usersURL + domainId + "/users/" + id + "/unlock", {});
  }

  search(domainId, searchTerm, page, size) {
    return this.http.get(this.usersURL + domainId + "/users?q=" + searchTerm + "&page=" + page + "&size=" + size);
  }

  consents(domainId, userId, clientId) {
    return this.http.get(this.usersURL + domainId + "/users/" + userId + "/consents" + (clientId ? "?clientId=" + clientId : ""));
  }

  revokeConsents(domainId, userId, clientId) {
    return this.http.delete(this.usersURL + domainId + "/users/" + userId + "/consents" + (clientId ? "?clientId=" + clientId : ""));
  }

  revokeConsent(domainId, userId, consentId) {
    return this.http.delete(this.usersURL + domainId + "/users/" + userId + "/consents/" + consentId);
  }
}
