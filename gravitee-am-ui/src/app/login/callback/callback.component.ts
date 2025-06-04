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
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, filter, switchMap, tap } from 'rxjs/operators';
import { toString } from 'lodash';

import { AuthService } from '../../services/auth.service';
import { SnackbarService } from '../../services/snackbar.service';

@Component({
  selector: 'app-login-callback',
  template: ``,
  standalone: false,
})
export class LoginCallbackComponent implements OnInit {
  constructor(
    private router: Router,
    private authService: AuthService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.authService
      .handleAuthentication()
      .pipe(
        filter((authentSuccess) => authentSuccess),
        switchMap(() => this.authService.userInfo()),
        tap((user) => {
          this.snackbarService.open('Login successful');
          if (user['newsletter_enabled'] && user['login_count'] === 1) {
            this.router.navigate(['/newsletter']);
          } else {
            this.router.navigate(['/']);
          }
        }),
        catchError((error: unknown) => {
          this.snackbarService.open(toString(error).replace(/%20/g, ' '));
          return this.router.navigate(['/logout']);
        }),
      )
      .subscribe();
  }
}
