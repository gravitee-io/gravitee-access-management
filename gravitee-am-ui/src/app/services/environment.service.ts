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
import { HttpClient } from '@angular/common/http';
import { Observable, ReplaySubject } from 'rxjs';
import { map, mergeMap } from 'rxjs/operators';

import { AuthService } from './auth.service';

import { AppConfig } from '../../config/app.config';

@Injectable()
export class EnvironmentService {
  public static NO_ENVIRONMENT = 'NO_ENVIRONMENT';

  private organizationURL = AppConfig.settings.organizationBaseURL;
  private currentEnvironment;
  private currentEnvironmentSubject = new ReplaySubject<any>(1);
  public currentEnvironmentObs$ = this.currentEnvironmentSubject.asObservable();
  private allEnvironments: any[];

  constructor(private http: HttpClient, private authService: AuthService) {}

  getAllEnvironments(): Observable<any[]> {
    if (this.allEnvironments) {
      return new Observable<any[]>((subscriber) => {
        subscriber.next(this.allEnvironments);
        subscriber.complete();
      });
    }

    return this.http.get<any>(this.organizationURL + '/environments').pipe(
      map((environments) => {
        this.allEnvironments = environments;
        return this.allEnvironments;
      }),
    );
  }

  getCurrentEnvironment(): any {
    return this.currentEnvironment;
  }

  setCurrentEnvironment(environment) {
    if (this.currentEnvironment !== environment) {
      this.currentEnvironment = environment;
      this.notifyEnvironment(this.currentEnvironment);
    }
  }

  getEnvironmentById(id: string): Observable<any> {
    return this.getAllEnvironments().pipe(
      map((environments) => {
        if (environments && environments.length > 0) {
          const find = environments.find((e) => e.id === id || e.hrids.includes(id));
          this.setCurrentEnvironment(find);
          return find;
        }

        return null;
      }),
    );
  }

  private notifyEnvironment(environment) {
    if (environment) {
      this.currentEnvironmentSubject.next(environment);
    }
  }

  permissions(id): Observable<any> {
    return this.getEnvironmentById(id).pipe(
      mergeMap((environment) => this.http.get<any>(this.organizationURL + '/environments/' + environment.id + '/members/permissions')),
      map((perms) => {
        this.authService.reloadEnvironmentPermissions(perms);
        return perms;
      }),
    );
  }
}
