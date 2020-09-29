import {flatMap, map, mergeMap} from 'rxjs/operators';
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
import {BehaviorSubject, Observable, Subject} from 'rxjs';
import {AppConfig} from '../../config/app.config';
import {EnvironmentService} from "./environment.service";
import {NavbarService} from "../components/navbar/navbar.service";

@Injectable()
export class AuthService {
  private userInfoUrl: string = AppConfig.settings.baseURL + '/user';
  private currentUser: any;
  private environmentPermissions: any[];
  private domainPermissions: any[];
  private applicationPermissions: any[];
  private environmentPermissionsSource = new BehaviorSubject(null);
  private environmentPermissionsObservable = this.environmentPermissionsSource.asObservable();
  private domainPermissionsSource = new BehaviorSubject(null);
  private domainPermissionsObservable = this.domainPermissionsSource.asObservable();
  private applicationPermissionsSource = new BehaviorSubject(null);
  private applicationPermissionsObservable = this.applicationPermissionsSource.asObservable();
  private subject = new Subject();
  notifyObservable$ = this.subject.asObservable();

  constructor(private http: HttpClient) {
    this.environmentPermissionsObservable.subscribe(permissions => this.environmentPermissions = permissions);
    this.domainPermissionsObservable.subscribe(permissions => this.domainPermissions = permissions);
    this.applicationPermissionsObservable.subscribe(permissions => this.applicationPermissions = permissions);
  }

  handleAuthentication(): Observable<boolean> {
    // authentication success
    return new Observable(observer => observer.next(true));
  }

  userInfo(): Observable<any> {
    return this.http.get<any>(this.userInfoUrl).pipe(
      map(user => {
        this.setUser(user);
        return this.currentUser;
      }));
  }

  setUser(user: any) {
    this.currentUser = user;
  }

  logout(): Observable<boolean> {
    return new Observable(observer => {
      observer.next(true);
    });
  }

  user() {
    return this.currentUser;
  }

  isAuthenticated(): boolean {
    return this.user() !== undefined;
  }

  hasPermissions(permissions): boolean {
    return this.isAuthenticated() &&
      permissions.every(v => this.user().permissions.indexOf(v) >= 0 ||
        (this.environmentPermissions && this.environmentPermissions.indexOf(v) >= 0) ||
        (this.domainPermissions && this.domainPermissions.indexOf(v) >= 0) ||
        (this.applicationPermissions && this.applicationPermissions.indexOf(v) >= 0));
  }

  hasAnyPermissions(permissions): boolean {

    return permissions.some(permission => this.hasPermissions([permission]) === true);
  }

  unauthorized() {
    this.subject.next('Unauthorized');
  }

  reloadEnvironmentPermissions(permissions) {
    this.environmentPermissionsSource.next(permissions);
  }

  reloadDomainPermissions(permissions) {
    this.domainPermissionsSource.next(permissions);
  }

  reloadApplicationPermissions(permissions) {
    this.applicationPermissionsSource.next(permissions);
  }

  environmentPermissionsLoaded(): boolean {
    return this.environmentPermissions != null;
  }

  domainPermissionsLoaded(): boolean {
    return this.domainPermissions != null;
  }

  applicationPermissionsLoaded(): boolean {
    return this.applicationPermissions != null;
  }
}
