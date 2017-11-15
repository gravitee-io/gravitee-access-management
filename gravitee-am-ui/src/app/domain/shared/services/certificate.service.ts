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
import {AppConfig} from "../../../../config/app.config";

@Injectable()
export class CertificateService {
  private certificatesURL = AppConfig.settings.baseURL + '/domains/';

  constructor(private http: Http) { }

  findByDomain(domainId): Observable<Response>  {
    return this.http.get(this.certificatesURL + domainId + "/certificates");
  }

  get(domainId, id): Observable<Response>  {
    return this.http.get(this.certificatesURL + domainId + "/certificates/" + id);
  }

  create(domainId, provider): Observable<Response>  {
    return this.http.post(this.certificatesURL + domainId + "/certificates", provider);
  }

  update(domainId, id, provider): Observable<Response>  {
    return this.http.put(this.certificatesURL + domainId + "/certificates/" + id, {
      'name' : provider.name,
      'configuration' : provider.configuration
    });
  }

  delete(domainId, id): Observable<Response>  {
    return this.http.delete(this.certificatesURL + domainId + "/certificates/" + id);
  }

  publicKey(domainId, id): Observable<Response>  {
    return this.http.get(this.certificatesURL + domainId + "/certificates/" + id + "/key");
  }

}
