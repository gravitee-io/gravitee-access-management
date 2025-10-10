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
import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
  HttpResponse,
  HttpResponseBase,
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { get } from 'lodash';

import { SnackbarService } from '../services/snackbar.service';
import { AuthService } from '../services/auth.service';
import { EnvironmentService } from '../services/environment.service';

@Injectable()
export class HttpRequestInterceptor implements HttpInterceptor {
  private xsrfToken: string;

  constructor(
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private environmentService: EnvironmentService,
    private router: Router,
  ) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    let orgId = 'DEFAULT';
    let envId = 'DEFAULT';

    if (this.authService.isAuthenticated()) {
      const user = this.authService.user();
      orgId = user.org;
    }

    if (this.environmentService.getCurrentEnvironment()) {
      envId = this.environmentService.getCurrentEnvironment().id;
    }
    request = request.clone({
      withCredentials: true,
      setHeaders: this.xsrfToken ? { 'X-Xsrf-Token': [this.xsrfToken] } : {},
      url: request.url.replace(':organizationId', orgId).replace(':environmentId', envId),
    });

    return next.handle(request).pipe(
      tap(
        (event: HttpEvent<any>) => {
          if (event instanceof HttpResponse) {
            this.saveXsrfToken(event);
          }
        },
        (err: unknown) => {
          if (get(err, 'status') === 404) {
            this.router.navigate(['/404']);
          }
          if (err instanceof HttpErrorResponse) {
            this.saveXsrfToken(err);
            if (err.status === 401) {
              this.snackbarService.open('The authentication session expires or the user is not authorized');
              this.authService.unauthorized();
            } else if (err.status === 403) {
              this.snackbarService.open('Access denied');
            } else {
              this.snackbarService.open(err.error.message || 'Server error');
            }
          }
        },
      ),
    );
  }

  private saveXsrfToken(response: HttpResponseBase) {
    const xsrfTokenHeader = response.headers.get('X-Xsrf-Token');

    if (xsrfTokenHeader !== null) {
      this.xsrfToken = xsrfTokenHeader;
    }
  }
}
