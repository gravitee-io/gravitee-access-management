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
import {MatHorizontalStepper, MatStepper} from '@angular/material/stepper';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../../../../services/snackbar.service';
import { DeviceNotifiersService } from 'app/services/device-notifiers.service';

@Component({
  selector: 'app-device-notifiers-creation',
  templateUrl: './device-notifiers-creation.component.html',
  styleUrls: ['./device-notifiers-creation.component.scss']
})
export class DeviceNotifiersCreationComponent implements OnInit {
  private domainId: string;
  deviceNotifier: any = {};
  configurationIsValid = true;
  @ViewChild('stepper', { static: true }) stepper: MatStepper;

  constructor(private notifiersService: DeviceNotifiersService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute) { }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
  }

  create() {
    this.deviceNotifier.configuration = JSON.stringify(this.deviceNotifier.configuration);
    this.notifiersService.create(this.domainId, this.deviceNotifier).subscribe(data => {
      this.snackbarService.open('Device Notifier ' + data.name + ' created');
      this.router.navigate(['..', data.id], { relativeTo: this.route });
    });
  }

  stepperValid() {
    return this.deviceNotifier &&
      this.deviceNotifier.name &&
      this.configurationIsValid;
  }

  setStepper(stepper: MatHorizontalStepper, step:number) {
    stepper.selectedIndex = step;
  }
}
