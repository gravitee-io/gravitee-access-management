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
import { AppConfig } from "../../config/app.config";
import { Observable } from "rxjs/Observable";
import { Http, Response } from "@angular/http";

@Injectable()
export class DashboardService {
  private dashboardURL = AppConfig.settings.baseURL + '/dashboard/';

  constructor(private http: Http) { }

  findClients(domainId: any): Observable<Response> {
    return this.http.get(this.dashboardURL + "clients" + ((domainId) ? "?domainId="+domainId : ""));
  }

  findTopClients(domainId: any): Observable<Response> {
    return this.http.get(this.dashboardURL + "clients/top" + ((domainId) ? "?domainId="+domainId : ""));
  }

  findTotalClients(domainId: any): Observable<Response> {
    return this.http.get(this.dashboardURL + "clients/total" + ((domainId) ? "?domainId="+domainId : ""));
  }

  findTotalTokens(domainId: any): Observable<Response> {
    return this.http.get(this.dashboardURL + "tokens" + ((domainId) ? "?domainId="+domainId : ""));
  }
}
