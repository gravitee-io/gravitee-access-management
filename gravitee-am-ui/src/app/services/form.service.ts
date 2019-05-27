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
export class FormService {
  private formsUrl = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: HttpClient) { }

  get(domainId, clientId, formTemplate): Observable<any> {
    return this.http.get<any>(this.formsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/forms?template=" + formTemplate);
  }

  create(domainId, clientId, form): Observable<any> {
    return this.http.post<any>(this.formsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/forms", form);
  }

  update(domainId, clientId, id, form): Observable<any> {
    return this.http.put<any>(this.formsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/forms/" + id, {
      'enabled' : form.enabled,
      'content' : form.content
    });
  }

  delete(domainId, clientId, id): Observable<any> {
    return this.http.delete<any>(this.formsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/forms/" + id);
  }

}
