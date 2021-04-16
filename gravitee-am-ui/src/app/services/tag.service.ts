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
export class TagService {
  private tagsURL = AppConfig.settings.organizationBaseURL + '/tags/';

  constructor(private http: HttpClient) {}

  list(): Observable<any> {
    return this.http.get<any>(this.tagsURL);
  }

  get(id): Observable<any> {
    return this.http.get<any>(this.tagsURL + id);
  }

  create(tag): Observable<any> {
    return this.http.post<any>(this.tagsURL, tag);
  }

  update(id, tag): Observable<any> {
    return this.http.put<any>(this.tagsURL + id, {
      name: tag.name,
      description: tag.description,
    });
  }

  delete(id): Observable<any> {
    return this.http.delete<any>(this.tagsURL + id);
  }
}
