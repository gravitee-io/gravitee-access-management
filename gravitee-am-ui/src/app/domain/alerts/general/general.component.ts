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
import { MatInput } from '@angular/material/input';
import {ActivatedRoute, Router} from '@angular/router';
import {DomainService} from '../../../services/domain.service';
import {DialogService} from '../../../services/dialog.service';
import {SnackbarService} from '../../../services/snackbar.service';
import {AuthService} from '../../../services/auth.service';
import {AlertService} from "../../../services/alert.service";
import {tap} from "rxjs/operators";
import {forkJoin, Observable} from 'rxjs';

export interface Tag {
  id: string;
  name: string;
}

@Component({
  selector: 'app-domain-alert-general',
  templateUrl: './general.component.html',
  styleUrls: ['./general.component.scss']
})
export class DomainAlertGeneralComponent implements OnInit {
  formChanged = false;
  domain: any = {};
  alertTriggers: any[];
  readonly = false;
  alertEnabled: boolean;
  alertNotifiers: any;

  constructor(private domainService: DomainService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute,
              private authService: AuthService,
              private alertService: AlertService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.alertNotifiers = this.route.snapshot.data['alertNotifiers'];
    this.alertEnabled = this.domain.alertEnabled;
    this.readonly = !this.authService.hasPermissions(['domain_alert_update']);
    this.loadAlertTriggers();
  }

  loadAlertTriggers() {
    this.alertService.getAlertTriggers(this.domain.id).subscribe(response => {
      this.alertTriggers = this.buildAlertTriggers(response);
    })
  }

  update() {
    let patchAlertSettings;
    let patchAlertTriggers

    if (this.domain.alertEnabled !== this.alertEnabled) {
      // Alert has been enabled or disabled. Need to call the api.
      patchAlertSettings = this.domainService.patchAlertSettings(this.domain.id, this.domain).pipe(
        tap(response => {
          this.domain = response;
          this.domainService.notify(this.domain);
        }));
    } else {
      patchAlertSettings = new Observable(subscriber => {
        subscriber.next(this.domain);
        subscriber.complete();
      });
    }

    let alertTriggersToPatch = this.alertTriggers.filter(alertTrigger => alertTrigger.available);

    // We assume to always send all available alert triggers on the domain. This ensures they are always in sync.
    patchAlertTriggers = this.alertService.patchAlertTriggers(this.domain.id, alertTriggersToPatch);

    forkJoin([patchAlertSettings, patchAlertTriggers])
      .subscribe(responses => {
        this.domain = responses[0];
        this.alertEnabled = this.domain.alertEnabled;
        this.domainService.notify(this.domain);
        this.formChanged = false;
        this.snackbarService.open('Domain alerts updated');
      })
  }

  buildAlertTriggers(alertTriggers: any[]) {
    let alertTriggerDefinitions = {
      too_many_login_failures: {
        name: 'Too many login failures',
        description: 'Alert when the number of login failures is abnormally high',
        icon: 'account_box',
        available: true
      },
      too_many_reset_passwords: {
        name: 'Too many reset passwords',
        description: 'Alert when the number of reset passwords is abnormally high',
        icon: 'lock',
        available: false
      },
      too_many_locked_out_users: {
        name: 'Too many locked out users',
        description: 'Alert when then number of user lockouts is abnormally high',
        icon: 'lock_open',
        available: false
      },
      slow_user_signing: {
        name: 'Slow user signin',
        description: 'Alert when the user sign-in phase is unusually slow',
        icon: 'account_box',
        available: false
      },
      too_many_user_registrations: {
        name: 'Too many user registrations',
        description: 'Alert when the number of user registrations is abnormally high',
        icon: 'person_add',
        available: false
      }
    }

    let allAlertTriggers = [];

    Object.keys(alertTriggerDefinitions).forEach(type => {
      let alertTriggerDefinition = alertTriggerDefinitions[type];
      alertTriggerDefinition.type = type;

      let alertTrigger = alertTriggers.find(alertTrigger => alertTrigger.type === type);

      if (alertTrigger !== undefined) {
        allAlertTriggers.push(Object.assign(alertTriggerDefinition, alertTrigger));
      } else {
        allAlertTriggers.push(alertTriggerDefinition);
      }
    })
    return allAlertTriggers;
  }

  getRowClass(row) {
    return {
      'row-disabled': !row.available
    };
  }

  isDisabled(row: any) {
    return this.readonly || !row.available;
  }
}
