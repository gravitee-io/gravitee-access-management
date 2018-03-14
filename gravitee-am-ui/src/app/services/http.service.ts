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
import { Http, XHRBackend, RequestOptions, RequestOptionsArgs, Request, Response } from '@angular/http';
import { Observable, Subject } from "rxjs";
import { SnackbarService } from "./snackbar.service";

@Injectable()
export class HttpService extends Http {
  private subject = new Subject();
  notifyObservable$ = this.subject.asObservable();

  constructor(backend: XHRBackend, defaultOptions: RequestOptions, private snackbarService: SnackbarService) {
    super(backend, defaultOptions);
  }

  request(url: string | Request, options?: RequestOptionsArgs): Observable<Response> {
    // set withCredentials option for every request
    if (typeof url === 'string') {
      if (!options) {
        options = { withCredentials: true };
      }
      options.withCredentials = true;
    } else {
      url.withCredentials = true;
    }
    return super.request(url, options).catch((error: Response) => {
      if (error.status === 401 || error.status === 403) {
        this.snackbarService.open('The authentication session expires or the user is not authorised');
        this.subject.next('Unauthorized');
      } else {
        this.snackbarService.open(error.json().message || 'Server error');
      }
      return Observable.throw(error);
    });
  }
}
