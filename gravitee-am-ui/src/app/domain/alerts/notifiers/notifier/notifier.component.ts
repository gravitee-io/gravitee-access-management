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
import { filter, switchMap, tap } from 'rxjs/operators';

import { AlertService } from '../../../../services/alert.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { DialogService } from '../../../../services/dialog.service';
import { OrganizationService } from '../../../../services/organization.service';

@Component({
  templateUrl: './notifier.component.html',
  styleUrls: ['./notifier.component.scss'],
})
export class DomainAlertNotifierComponent implements OnInit {
  domain: any = {};
  entrypoint: any = {};
  configurationIsValid = true;
  configurationPristine = true;
  notifierSchema: any;
  alertNotifier: any;
  alertNotifierConfiguration: any;
  updateAlertNotifierConfiguration: any;
  redirectUri: string;

  constructor(
    private alertService: AlertService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private router: Router,
    private dialogService: DialogService,
    private organizationService: OrganizationService,
  ) {}

  ngOnInit() {
    this.alertNotifier = this.route.snapshot.data['alertNotifier'];
    this.domain = this.route.snapshot.data['domain'];
    this.alertNotifierConfiguration = JSON.parse(this.alertNotifier.configuration);
    this.updateAlertNotifierConfiguration = this.alertNotifierConfiguration;

    this.organizationService.notifierSchema(this.alertNotifier.type).subscribe((data) => {
      this.notifierSchema = data;
      Object.keys(this.notifierSchema['properties']).forEach((key) => {
        this.notifierSchema['properties'][key].default = '';
      });
    });
  }

  update() {
    this.alertNotifier.configuration = this.updateAlertNotifierConfiguration;
    this.alertService.patchAlertNotifier(this.domain.id, this.alertNotifier).subscribe((data) => {
      this.snackbarService.open('Alert notifier updated');
      this.alertNotifier = data;
      this.alertNotifierConfiguration = JSON.parse(this.alertNotifier.configuration);
      this.updateAlertNotifierConfiguration = this.alertNotifierConfiguration;
      this.configurationPristine = true;
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Alert Notifier', 'Are you sure you want to delete this alert notifier ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.alertService.deleteAlertNotifier(this.domain.id, this.alertNotifier.id)),
        tap(() => {
          this.snackbarService.open('Alert notifier deleted');
          this.router.navigate(['../..'], { relativeTo: this.route });
        }),
      )
      .subscribe();
  }

  enableProviderUpdate(configurationWrapper) {
    window.setTimeout(() => {
      this.configurationPristine = this.alertNotifier.configuration === JSON.stringify(configurationWrapper.configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updateAlertNotifierConfiguration = configurationWrapper.configuration;
    });
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }
}
