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
import {Component, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../../../services/snackbar.service';
import {ApplicationService} from '../../../../../services/application.service';
import {DialogService} from '../../../../../services/dialog.service';
import {AuthService} from '../../../../../services/auth.service';
import * as _ from 'lodash';

@Component({
  selector: 'application-general',
  templateUrl: './general.component.html',
  styleUrls: ['./general.component.scss']
})
export class ApplicationGeneralComponent implements OnInit {
  @ViewChild('applicationForm') form: any;
  private domainId: string;
  domain: any;
  application: any;
  applicationOAuthSettings: any = {};
  applicationAdvancedSettings: any = {};
  redirectUri: string;
  redirectUris: any[] = [];
  formChanged = false;
  editMode: boolean;
  deleteMode: boolean;
  renewSecretMode: boolean;
  applicationType: string;
  applicationTypes: any[] = [
    {
      name: 'Web',
      type: 'WEB'
    },
    {
      name: 'Single-Page App',
      type: 'BROWSER'
    },
    {
      name: 'Native',
      type: 'NATIVE'
    },
    {
      name: 'Backend to Backend',
      type: 'SERVICE'
    }];

  constructor(private route: ActivatedRoute,
              private router: Router,
              private snackbarService: SnackbarService,
              private applicationService: ApplicationService,
              private authService: AuthService,
              private dialogService: DialogService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainId = this.domain.id;
    this.application = this.route.snapshot.parent.parent.data['application'];
    this.applicationType = this.application.type.toUpperCase();
    this.applicationOAuthSettings = this.application.settings == null ? {} : this.application.settings.oauth || {};
    this.applicationAdvancedSettings = this.application.settings == null ? {} : this.application.settings.advanced || {};
    this.applicationOAuthSettings.redirectUris = this.applicationOAuthSettings.redirectUris || [];
    this.application.factors = this.application.factors || [];
    this.redirectUris = _.map(this.applicationOAuthSettings.redirectUris, function (item) {
      return {value: item};
    });
    this.editMode = this.authService.hasPermissions(['application_settings_update']);
    this.deleteMode = this.authService.hasPermissions(['application_settings_delete']);
    this.renewSecretMode = this.authService.hasPermissions(['application_oauth2_update']);
  }


  update() {
    const data: any = {};
    data.name = this.application.name;
    data.type = this.applicationType;
    data.description = this.application.description;
    data.settings = {};
    data.settings.oauth = { 'redirectUris' : _.map(this.redirectUris, 'value') };
    data.settings.advanced = { 'skipConsent' : this.applicationAdvancedSettings.skipConsent };
    this.applicationService.patch(this.domainId, this.application.id, data).subscribe(response => {
      this.application = response;
      this.application.type = this.application.type.toUpperCase();
      this.form.reset(this.application);
      this.formChanged = false;
      this.snackbarService.open('Application updated');
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Application', 'Are you sure you want to delete this application ?')
      .subscribe(res => {
        if (res) {
          this.applicationService.delete(this.domainId, this.application.id).subscribe(response => {
            this.snackbarService.open('Application deleted');
            this.router.navigate(['/domains', this.domainId, 'applications']);
          });
        }
      });
  }

  renewClientSecret(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Renew Client secret', 'Are you sure you want to renew the client secret ?')
      .subscribe(res => {
        if (res) {
          this.applicationService.renewClientSecret(this.domainId, this.application.id).subscribe(data => {
            this.application = data;
            this.snackbarService.open('Client secret updated');
          });
        }
      });
  }

  addRedirectUris(event) {
    event.preventDefault();
    if (!this.redirectUris.some(el => el.value === this.redirectUri)) {
      this.redirectUris.push({value: this.redirectUri});
      this.redirectUris = [...this.redirectUris];
      this.redirectUri = null;
      this.formChanged = true;
    } else {
      this.snackbarService.open(`Error : redirect URI "${this.redirectUri}" already exists`);
    }
  }

  deleteRedirectUris(redirectUri, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Remove redirect URI', 'Are you sure you want to remove this redirect URI ?')
      .subscribe(res => {
        if (res) {
          _.remove(this.redirectUris, { value: redirectUri });
          this.redirectUris = [...this.redirectUris];
          this.formChanged = true;
        }
      });
  }

  enableAutoApprove(event) {
    this.applicationAdvancedSettings.skipConsent = event.checked;
    this.formChanged = true;
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  displaySection(): boolean {
    return this.application.type !== 'SERVICE';
  }
}
