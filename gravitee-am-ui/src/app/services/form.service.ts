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
import { Http, Response } from "@angular/http";
import { Observable } from "rxjs";
import { AppConfig } from "../../config/app.config";

@Injectable()
export class FormService {
  private formsUrl = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: Http) { }

  get(domainId, clientId, formTemplate): Observable<Response>  {
    return this.http.get(this.formsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/forms?template=" + formTemplate);
  }

  create(domainId, clientId, form): Observable<Response>  {
    return this.http.post(this.formsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/forms", form);
  }

  update(domainId, clientId, id, form): Observable<Response>  {
    return this.http.put(this.formsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/forms/" + id, {
      'enabled' : form.enabled,
      'content' : form.content
    });
  }

  delete(domainId, clientId, id): Observable<Response>  {
    return this.http.delete(this.formsUrl + domainId + (clientId ? "/clients/" + clientId : "") + "/forms/" + id);
  }

}
