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
import { ActivatedRoute, Router } from '@angular/router';

import { ApplicationService } from '../../../../../services/application.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { AuthService } from '../../../../../services/auth.service';

@Component({
  selector: 'app-application-login-settings',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss'],
})
export class ApplicationLoginSettingsComponent implements OnInit {
  private domainId: string;
  application: any;
  loginSettings: any;
  readonly = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private applicationService: ApplicationService,
    private authService: AuthService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = JSON.parse(JSON.stringify(this.route.snapshot.data['application']));
    this.loginSettings = this.application.settings.login || { inherited: true };
    this.readonly = !this.authService.hasPermissions(['application_settings_update']);
  }

  updateLoginSettings(loginSettings) {
    this.loginSettings = loginSettings;
    this.applicationService.patch(this.domainId, this.application.id, { settings: { login: loginSettings } }).subscribe(() => {
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
    });
  }
}
