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
import {MatStepper} from '@angular/material';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../../services/snackbar.service';
import {AlertService} from "../../../../services/alert.service";

@Component({
  selector: 'alert-notifier-creation',
  templateUrl: './notifier-creation.component.html',
  styleUrls: ['./notifier-creation.component.scss']
})
export class DomainAlertNotifierCreationComponent implements OnInit {
  public alertNotifier: any = {};
  private domain: any;
  configurationIsValid = false;
  @ViewChild('stepper', {static: true}) stepper: MatStepper;

  constructor(private alertService: AlertService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute) {
  }

  ngOnInit(): void {
    this.domain = this.route.snapshot.data['domain'];
  }

  create() {
    // By default, the notifier should be enabled when created.
    this.alertNotifier.enabled = true;
    this.alertService.createAlertNotifier(this.domain.id, this.alertNotifier).subscribe(data => {
      this.snackbarService.open('Alert notifier ' + data.name + ' created');
      this.router.navigate(['..', data.id], {relativeTo: this.route, queryParams: {reload: true}});
    });
  }

  stepperValid() {
    return this.alertNotifier && this.alertNotifier.name && this.configurationIsValid;
  }
}
