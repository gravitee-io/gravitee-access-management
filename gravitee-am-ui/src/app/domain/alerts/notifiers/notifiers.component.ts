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
import {ProviderService} from "../../../services/provider.service";
import {SnackbarService} from "../../../services/snackbar.service";
import {DialogService} from "../../../services/dialog.service";
import {ActivatedRoute, Router} from "@angular/router";
import {OrganizationService} from "../../../services/organization.service";
import {AlertService} from "../../../services/alert.service";

@Component({
  selector: 'app-domain-alert-notifiers',
  templateUrl: './notifiers.component.html',
  styleUrls: ['./notifiers.component.scss']
})
export class DomainAlertNotifiersComponent implements OnInit {
  private notifiersByType: any;
  private domain: any;
  alertNotifiers: any[];

  constructor(private organizationService: OrganizationService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router,
              private alertService: AlertService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    let availableNotifiers = this.route.snapshot.data['notifiers'];
    this.notifiersByType = availableNotifiers.reduce((map, notifier) => {
      map[notifier.id] = notifier
      return map
    }, {})

    this.alertNotifiers = this.route.snapshot.data['alertNotifiers'];
  }

  get isEmpty(): boolean {
    return !this.alertNotifiers || this.alertNotifiers.length === 0;
  }

  getNotifierIcon(type) {
    const notifier = this.notifiersByType[type];
    if (notifier && notifier.icon) {
      return `<img width="24" height="24" src="${notifier.icon}" alt="${notifier.name} image" title="${notifier.name}"/>`;
    }
    return `<span class="material-icons">notifications</span>`;
  }

  isNotifierAvailable(type) {
    return this.notifiersByType[type] !== undefined;
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Alert Notifier', 'Are you sure you want to delete this alert notifier ?')
      .subscribe(res => {
        if (res) {
          this.alertService.deleteAlertNotifier(this.domain.id, id).subscribe(response => {
            this.snackbarService.open('Alert notifier deleted');
            this.loadAlertNotifiers();
          });
        }
      });
  }

  loadAlertNotifiers() {
    this.alertService.getAlertNotifiers(this.domain.id)
      .subscribe(response => this.alertNotifiers = response);
  }
}
