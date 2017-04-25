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
import { BehaviorSubject, Observable } from "rxjs";
import { Http, Response } from "@angular/http";
import { AppConfig } from "../../config/app.config";

@Injectable()
export class DomainService {
  private subject = new BehaviorSubject<any>({});
  notifyObservable$ = this.subject.asObservable();
  private domainsURL: string = AppConfig.settings.baseURL + '/management/domains/';

  constructor(private http: Http) {}

  list(): Observable<Response> {
    return this.http.get(this.domainsURL);
  }

  get(id: string): Observable<Response>  {
    return this.http.get(this.domainsURL + id);
  }

  create(domain): Observable<Response>  {
    return this.http.post(this.domainsURL, domain);
  }

  update(id, domain): Observable<Response> {
    return this.http.put(this.domainsURL + id, {
      'name': domain.name,
      'description': domain.description,
      'path': domain.path,
      'enabled': domain.enabled});
  }

  delete(id): Observable<Response> {
    return this.http.delete(this.domainsURL + id);
  }

  notify(data: any) {
    if (data) {
      this.subject.next(data);
    }
  }
}
