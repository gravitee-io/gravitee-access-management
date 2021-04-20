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
import { AppConfig } from '../../config/app.config';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable()
export class EntrypointService {
  private entrypointsURL = AppConfig.settings.organizationBaseURL + '/entrypoints/';

  constructor(private http: HttpClient) {}

  list(): Observable<any> {
    return this.http.get<any>(this.entrypointsURL);
  }

  get(id): Observable<any> {
    return this.http.get<any>(this.entrypointsURL + id);
  }

  create(entrypoint): Observable<any> {
    return this.http.post<any>(this.entrypointsURL, entrypoint);
  }

  update(id, entrypoint): Observable<any> {
    return this.http.put<any>(this.entrypointsURL + id, {
      name: entrypoint.name,
      description: entrypoint.description,
      url: entrypoint.url,
      tags: entrypoint.tags,
    });
  }

  delete(id): Observable<any> {
    return this.http.delete<any>(this.entrypointsURL + id);
  }
}
