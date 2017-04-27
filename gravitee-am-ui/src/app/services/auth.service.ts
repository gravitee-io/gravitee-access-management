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
  private baseUrl: string = AppConfig.settings.baseURL;
  private domainId: string = AppConfig.settings.domainId;
  private clientId: string = AppConfig.settings.clientId;
  private redirectUri: string = AppConfig.settings.redirectUri;
  private responseType: string = 'token';
  private _authorizationEndpoint: string =
    this.baseUrl + '/' + this.domainId +
    '/oauth/authorize?client_id=' + this.clientId +
    '&response_type=' + this.responseType +
    '&redirect_uri='+ this.redirectUri;
  private userInfoUrl: string = this.baseUrl + '/' + this.domainId + '/userinfo';
  private CALLBACK_ACCESS_TOKEN_PATTERN: string = '#access_token=(.*)';
  private currentUser: any;
  private subject = new Subject();
  notifyObservable$ = this.subject.asObservable();

  constructor(private http: Http) {
    this.currentUser = sessionStorage.getItem('user');
  }

  handleAuthentication(): Observable<boolean> {
    return Observable.create(observer => {
      let href = window.location.href;
      let oauthCallbackParameters = href.match(this.CALLBACK_ACCESS_TOKEN_PATTERN);
      if (oauthCallbackParameters && oauthCallbackParameters.length > 1) {
        let rawAccessToken = 'access_token=' + oauthCallbackParameters[1];
        let accessTokenArray = rawAccessToken.split("&");
        let accessTokenMap = [];
        for(let i = 0; i < accessTokenArray.length; i++) {
          accessTokenMap[accessTokenArray[i].split("=")[0]] = accessTokenArray[i].split("=")[1];
        }
        if (accessTokenMap['access_token']) {
          sessionStorage.setItem("access_token", accessTokenMap['access_token']);
          observer.next(true);
        } else {
          observer.next(false);
        }
      } else {
        observer.next(false);
      }
    });
  }

  authorizationEndpoint(): string {
    return this._authorizationEndpoint;
  }

  userInfo(): Observable<Response> {
    return this.http.get(this.userInfoUrl).map(res => {
      this.setUser(res.json().name);
      return res;
    });
  }

  setUser(user: any) {
    this.currentUser = user;
    sessionStorage.setItem('user', this.currentUser);
  }

  logout() {
    sessionStorage.removeItem('user');
    sessionStorage.removeItem('access_token');
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
