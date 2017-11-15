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
import { AppConfig } from "../../../config/app.config";

@Injectable()
export class AuthService {
  private domainId: string = AppConfig.settings.domainId;
  private clientId: string = AppConfig.settings.authentication.oauth2.clientId;
  private redirectUri: string = AppConfig.settings.authentication.oauth2.redirectUri;
  private responseType: string = 'token';
  private _authorizationEndpoint: string =
    AppConfig.settings.authentication.oauth2.authorize + '?client_id=' + this.clientId +
    '&response_type=' + this.responseType +
    '&scope=openid' +
    '&redirect_uri=' + this.redirectUri;
  private userInfoUrl: string = AppConfig.settings.authentication.oauth2.userInfo;
  private _logoutEndpoint: string = AppConfig.settings.authentication.oauth2.logoutUri;
  private CALLBACK_ACCESS_TOKEN_PATTERN: string = '#access_token=(.*)';
  private CALLBACK_ERROR_PATTERN: string = '#error=(.*)';
  private CALLBACK_ERROR_DESCRIPTION_PATTERN: string = 'error_description=(.*)';
  private currentUser: any;
  private subject = new Subject();
  notifyObservable$ = this.subject.asObservable();

  constructor(private http: Http) {
    this.currentUser = sessionStorage.getItem('user');
  }

  handleAuthentication(): Observable<boolean> {
    let href = window.location.href;
    // check error callback
    let oauthErrorCallback = href.match(this.CALLBACK_ERROR_PATTERN);
    if (oauthErrorCallback) {
      let oauthErrorDescriptionCallback = href.match(this.CALLBACK_ERROR_DESCRIPTION_PATTERN);
      return Observable.throw(oauthErrorDescriptionCallback[1]);
    }

    // check missing access token
    let oauthCallbackParameters = href.match(this.CALLBACK_ACCESS_TOKEN_PATTERN);
    if (!oauthCallbackParameters || oauthCallbackParameters.length <= 1) {
      return Observable.throw('Missing access token in response');
    }

    return Observable.create(observer => {
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
    });
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
    this.currentUser = user.sub;
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
