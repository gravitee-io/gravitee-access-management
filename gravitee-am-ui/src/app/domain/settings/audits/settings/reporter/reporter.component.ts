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
import {OrganizationService} from '../../../../../services/organization.service';
import {ReporterService} from '../../../../../services/reporter.service';
import {SnackbarService} from '../../../../../services/snackbar.service';

@Component({
  selector: 'app-reporter',
  templateUrl: './reporter.component.html',
  styleUrls: ['./reporter.component.scss']
})
export class ReporterComponent implements OnInit {
  @ViewChild('reporterForm') form: any;

  private domainId: string;
  private organizationContext = false;
  configurationIsValid: boolean = true;
  configurationPristine: boolean = true;
  reporterSchema: any;
  reporter: any;
  reporterConfiguration: any;
  updateReporterConfiguration: any;
  formChanged: boolean = false;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private organizationService: OrganizationService,
              private reporterService: ReporterService,
              private snackbarService: SnackbarService) {
  }
  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain'].id;

    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
    this.reporter = this.route.snapshot.data['reporter'];
    this.reporterConfiguration = JSON.parse(this.reporter.configuration);
    this.updateReporterConfiguration = this.reporterConfiguration;
    this.organizationService.reporterSchema(this.reporter.type).subscribe(data => {
      this.reporterSchema = data;
      // handle default null values
      let self = this;
      Object.keys(this.reporterSchema['properties']).forEach(function(key) {
        self.reporterSchema['properties'][key].default = '';
      });
    });
  }

  update() {
    this.reporter.configuration = JSON.stringify(this.updateReporterConfiguration);
    this.reporterService.update(this.domainId, this.reporter.id, this.reporter, this.organizationContext).subscribe(data => {
      this.reporter = data;
      this.reporterConfiguration = JSON.parse(this.reporter.configuration);
      this.updateReporterConfiguration = this.reporterConfiguration;
      this.formChanged = false;
      this.form.reset(this.reporter);
      this.snackbarService.open('Reporter updated');
    });
  }

  enableReporterUpdate(configurationWrapper) {
    window.setTimeout(() => {
      this.configurationPristine = this.reporter.configuration === JSON.stringify(configurationWrapper.configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updateReporterConfiguration = configurationWrapper.configuration;
    });
  }

  enableReporter(event) {
    this.reporter.enabled = event.checked;
    this.formChanged = true;
  }
}
