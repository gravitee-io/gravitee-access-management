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
import {HttpClient} from '@angular/common/http';
import {AuthService} from './auth.service';
import {AppConfig} from '../../config/app.config';
import {Observable, Subject} from 'rxjs';
import {map, tap} from 'rxjs/operators';
import {DomainService} from "./domain.service";

@Injectable()
export class AlertService {
  private alertsURL: string = AppConfig.settings.domainBaseURL;

  constructor(private http: HttpClient, private domainService: DomainService) {
  }

  getAlertTriggers(domainId: string): Observable<any[]> {
    return this.http.get<any>(this.alertsURL + domainId + '/alerts/triggers');
  }

  patchAlertTriggers(domainId: string, alertTriggers: any[]): Observable<any> {
    let alertTriggersPatch = alertTriggers.map(alertTrigger => {
      return {
        'type': alertTrigger.type,
        'enabled': alertTrigger.enabled,
        'alertNotifiers': alertTrigger.alertNotifiers
      }
    });

    return this.http.patch<any>(this.alertsURL + domainId + '/alerts/triggers', alertTriggersPatch);
  }

  getAlertNotifiers(domainId: string): Observable<any[]> {
    return this.http.get<any>(this.alertsURL + domainId + '/alerts/notifiers');
  }

  getAlertNotifier(domainId: string, alertNotifierId: string): Observable<any> {
    return this.http.get<any>(this.alertsURL + domainId + '/alerts/notifiers/' + alertNotifierId);
  }

  createAlertNotifier(domainId, alertNotifier: any) {
    alertNotifier.configuration = JSON.stringify(alertNotifier.configuration);
    return this.http.post<any>(this.alertsURL + domainId + '/alerts/notifiers', alertNotifier);
  }

  deleteAlertNotifier(domainId, alertNotifierId: any) {
    return this.http.delete<any>(this.alertsURL + domainId + '/alerts/notifiers/' + alertNotifierId);
  }

  patchAlertNotifier(domainId: string, alertNotifier: any): Observable<any> {
    let alertNotifierPatch = {
      'name': alertNotifier.name,
      'enabled': alertNotifier.enabled,
      'configuration': JSON.stringify(alertNotifier.configuration),
    };

    return this.http.patch<any>(this.alertsURL + domainId + '/alerts/notifiers/' + alertNotifier.id, alertNotifierPatch);
  }
}
