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
import {MatStepper} from '@angular/material/stepper';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../../services/snackbar.service';
import {BotDetectionService} from "../../../../services/bot-detection.service";
import {DeviceIdentifierService} from "../../../../services/device-identifier.service";

@Component({
  selector: 'app-device-identifiers-creation',
  templateUrl: './device-identifier-creation.component.html',
  styleUrls: ['./device-identifier-creation.component.scss']
})
export class DeviceIdentifierCreationComponent implements OnInit {
  private domainId: string;
  deviceIdentifier: any = {};
  configurationIsValid = true;
  @ViewChild('stepper', {static: true}) stepper: MatStepper;

  constructor(private deviceIdentifierService: DeviceIdentifierService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute) {
  }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
  }

  create() {
    this.deviceIdentifier.configuration = JSON.stringify(this.deviceIdentifier.configuration);
    if (this.deviceIdentifier.configuration === null || !this.deviceIdentifier.configuration || this.deviceIdentifier.configuration == "") {
      this.deviceIdentifier.configuration = "{}";
    }
    this.deviceIdentifierService.create(this.domainId, this.deviceIdentifier).subscribe(data => {
      this.snackbarService.open('Device identifier ' + data.name + ' created');
      this.router.navigate(['..', data.id], {relativeTo: this.route});
    });
  }

  stepperValid() {
    return this.deviceIdentifier &&
      this.deviceIdentifier.name &&
      this.configurationIsValid;
  }
}
