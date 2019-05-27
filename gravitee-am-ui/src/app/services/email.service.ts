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
import { AppConfig } from "../../config/app.config";
import { Observable } from "rxjs";

@Injectable()
export class EmailService {
  private emailsUrl = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: HttpClient) { }

  get(domainId, clientId, emailTemplate): Observable<any> {
    return this.http.get<any>(this.emailsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/emails?template=" + emailTemplate);
  }

  create(domainId, clientId, email): Observable<any> {
    return this.http.post<any>(this.emailsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/emails", email);
  }

  update(domainId, clientId, id, email): Observable<any> {
    return this.http.put<any>(this.emailsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/emails/" + id, {
      'enabled' : email.enabled,
      'from': email.from,
      'fromName': email.fromName,
      'subject': email.subject,
      'expiresAfter': email.expiresAfter,
      'content' : email.content
    });
  }

  delete(domainId, clientId, id): Observable<any> {
    return this.http.delete<any>(this.emailsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/emails/" + id);
  }

}
