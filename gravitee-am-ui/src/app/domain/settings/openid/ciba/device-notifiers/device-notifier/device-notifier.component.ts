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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import { AuthService } from 'app/services/auth.service';
import { DeviceNotifiersService } from 'app/services/device-notifiers.service';
import { DialogService } from 'app/services/dialog.service';
import { OrganizationService } from 'app/services/organization.service';
import { SnackbarService } from 'app/services/snackbar.service';

@Component({
  selector: 'app-device-notifier',
  templateUrl: './device-notifier.component.html',
  styleUrls: ['./device-notifier.component.scss']
})
export class DeviceNotifierComponent implements OnInit {
  private domainId: string;
  formChanged = false;
  configurationIsValid = true;
  configurationPristine = true;
  deviceNotifier: any;
  deviceNotifierSchema: any;
  deviceNotifierConfiguration: any;
  updateDeviceNotifierConfiguration: any;
  editMode: boolean;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private organizationService: OrganizationService,
              private notifierService: DeviceNotifiersService,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private authService: AuthService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.deviceNotifier = this.route.snapshot.data['deviceNotifier'];
    this.deviceNotifierConfiguration = JSON.parse(this.deviceNotifier.configuration);
    this.updateDeviceNotifierConfiguration = this.deviceNotifierConfiguration;
    this.editMode = this.authService.hasPermissions(['domain_authdevice_notifier_update']);

    this.organizationService.deviceNotifierSchema(this.deviceNotifier.type).subscribe(data => {
      this.deviceNotifierSchema = data;
    });
  }

  update() {
    this.deviceNotifier.configuration = JSON.stringify(this.updateDeviceNotifierConfiguration);
    this.notifierService.update(this.domainId, this.deviceNotifier.id, this.deviceNotifier).subscribe(data => {
      this.snackbarService.open('Device Notifier updated');
    })
  }

  enableDeviceNotifierUpdate(configurationWrapper) {
    window.setTimeout(() => {
      this.configurationPristine = this.deviceNotifier.configuration === JSON.stringify(configurationWrapper.configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updateDeviceNotifierConfiguration = configurationWrapper.configuration;
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Device Notifier', 'Are you sure you want to delete this notifier ?')
      .subscribe(res => {
        if (res) {
          this.notifierService.delete(this.domainId, this.deviceNotifier.id).subscribe(() => {
            this.snackbarService.open('Device Notifier deleted');
            this.router.navigate(['..'], { relativeTo: this.route });
          });
        }
      });
  }
}
