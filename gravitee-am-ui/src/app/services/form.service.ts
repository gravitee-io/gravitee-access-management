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
import {HttpClient} from "@angular/common/http";
import {AppConfig} from "../../config/app.config";
import {Observable} from "rxjs";
import {OrganizationService} from "./organization.service";

@Injectable()
export class FormService {
  private formsUrl = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient,
              private organizationService: OrganizationService) {
  }

  get(domainId, appId, formTemplate): Observable<any> {
    return this.http.get<any>(this.formsUrl + domainId + (appId ? "/applications/" + appId : "") + "/forms?template=" + formTemplate);
  }

  create(domainId, appId, form, organizationContext): Observable<any> {
    if(organizationContext) {
      return this.organizationService.createForm(form);
    }
    return this.http.post<any>(this.formsUrl + domainId + (appId ? "/applications/" + appId : "") + "/forms", form);
  }

  update(domainId, appId, id, form, organizationContext): Observable<any> {
    if(organizationContext) {
      return this.organizationService.updateForm(id, form);
    }
    return this.http.put<any>(this.formsUrl + domainId + (appId ? "/applications/" + appId : "") + "/forms/" + id, {
      'enabled': form.enabled,
      'content': form.content
    });
  }

  delete(domainId, appId, id, organizationContext): Observable<any> {
    if(organizationContext) {
      return this.organizationService.deleteForm(id);
    }
    return this.http.delete<any>(this.formsUrl + domainId + (appId ? "/applications/" + appId : "") + "/forms/" + id);
  }

  preview(domainId, preview) {
    return this.http.post<any>(this.formsUrl + domainId + "/forms/preview", preview);
  }
}
