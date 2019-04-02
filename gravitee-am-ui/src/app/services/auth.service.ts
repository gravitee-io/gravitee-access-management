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
import { Observable, Subject } from "rxjs";
import { Http, Response } from "@angular/http";
import { AppConfig } from "../../config/app.config";

@Injectable()
export class AuthService {
  private domainId: string = AppConfig.settings.domainId;
  private clientId: string = AppConfig.settings.authentication.clientId;
  private redirectUri: string = AppConfig.settings.authentication.redirectUri;
  private _authorizationEndpoint: string = AppConfig.settings.authentication.authorize + '?redirect_uri=' + this.redirectUri;
  private userInfoUrl: string = AppConfig.settings.baseURL + '/user';
  private _logoutEndpoint: string = AppConfig.settings.authentication.logoutUri;
  private currentUser: any;
  private subject = new Subject();
  notifyObservable$ = this.subject.asObservable();

  constructor(private http: Http) {
    this.currentUser = sessionStorage.getItem('user');
  }

  handleAuthentication(): Observable<boolean> {
    // authentication success
    return Observable.create(observer => observer.next(true));
  }

  authorizationEndpoint(): string {
    return this._authorizationEndpoint;
  }

  logoutEndpoint(): string {
    return this._logoutEndpoint;
  }

  userInfo(): Observable<Response> {
    return this.http.get(this.userInfoUrl).map(res => {
      this.setUser(res.json());
      return res;
    });
  }

  setUser(user: any) {
    this.currentUser = user.preferred_username;
    sessionStorage.setItem('user', this.currentUser);
  }

  logout(): Observable<boolean> {
    return Observable.create(observer => {
      sessionStorage.clear();
      observer.next(true);
    });
  }

  user() {
    return this.currentUser;
  }

  isAuthenticated(): boolean {
    return this.user() !== undefined;
  }

  unauthorized() {
    this.subject.next("Unauthorized");
  }
}
