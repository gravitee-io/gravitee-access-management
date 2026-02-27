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
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatStepper } from '@angular/material/stepper';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs/operators';

import { AuthorizationEngineService } from '../../../services/authorization-engine.service';
import { SnackbarService } from '../../../services/snackbar.service';

@Component({
  selector: 'app-authorization-engine-creation',
  templateUrl: './authorization-engine-creation.component.html',
  styleUrls: ['./authorization-engine-creation.component.scss'],
  standalone: false,
})
export class AuthorizationEngineCreationComponent implements OnInit {
  public authorizationEngine: any = {};
  private domainId: string;
  configurationIsValid = false;
  isCreating = false;
  @ViewChild('stepper', { static: true }) stepper: MatStepper;

  constructor(
    private authorizationEngineService: AuthorizationEngineService,
    private snackbarService: SnackbarService,
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
  }

  create() {
    if (this.isCreating) {
      return;
    }

    this.isCreating = true;

    this.authorizationEngineService
      .create(this.domainId, this.authorizationEngine)
      .pipe(
        finalize(() => {
          this.isCreating = false;
        }),
      )
      .subscribe({
        next: (data) => {
          this.snackbarService.open('Authorization engine ' + data.name + ' created');
          const route = data.type === 'sidecar' ? 'sidecar' : 'openfga';
          this.router.navigate(['..', data.id, route], { relativeTo: this.route });
        },
      });
  }

  stepperValid() {
    return this.authorizationEngine?.name && this.configurationIsValid;
  }
}
