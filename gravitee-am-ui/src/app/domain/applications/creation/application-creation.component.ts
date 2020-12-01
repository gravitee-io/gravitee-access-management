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
import {ApplicationService} from '../../../services/application.service';
import {SnackbarService} from '../../../services/snackbar.service';

@Component({
  selector: 'app-creation',
  templateUrl: './application-creation.component.html',
  styleUrls: ['./application-creation.component.scss']
})
export class ApplicationCreationComponent implements OnInit {
  public application: any = {};
  private domainId: string;
  @ViewChild('stepper', { static: true }) stepper: MatStepper;

  constructor(private applicationService: ApplicationService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.params['domainId'];
    this.application.domain = this.domainId;
  }

  create() {
    const app: any = {};
    app.name = this.application.name;
    app.type = this.application.type;
    app.description = this.application.description;
    app.clientId = this.application.clientId;
    app.clientSecret = this.application.clientSecret;
    app.redirectUris = this.application.redirectUri ? [this.application.redirectUri] : null;

    this.applicationService.create(this.application.domain, app).subscribe(data => {
      this.snackbarService.open('Application ' + data.name + ' created');
      // needed to trick reuse route strategy, skipLocationChange to avoid /dummy to go into history
      this.router.navigateByUrl('/dummy', { skipLocationChange: true })
        .then(() => this.router.navigate(['/domains', this.application.domain, 'applications', data.id]));
    });
  }

  stepperValid() {
    return this.application &&
      this.application.type &&
      this.application.domain &&
      this.application.name &&
      (this.application.type !== 'SERVICE' ? this.application.redirectUri : true);
  }
}
