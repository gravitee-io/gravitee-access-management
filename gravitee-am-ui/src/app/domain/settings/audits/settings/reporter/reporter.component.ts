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
import {DialogService} from 'app/services/dialog.service';
import {NavbarService} from 'app/components/navbar/navbar.service';

@Component({
  selector: 'app-reporter',
  templateUrl: './reporter.component.html',
  styleUrls: ['./reporter.component.scss']
})
export class ReporterComponent implements OnInit {
  @ViewChild('reporterForm', {static: true}) form: any;

  private domainId: string;
  private organizationContext = false;
  createMode = false;
  configurationIsValid: boolean = true;
  configurationPristine: boolean = true;
  reporterSchema: any;
  reporter: any;
  plugins: any;
  reporterConfiguration: any;
  updateReporterConfiguration: any;
  formChanged: boolean = false;
  hasName: boolean = false;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private organizationService: OrganizationService,
              private reporterService: ReporterService,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private navbarService: NavbarService) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;

    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }

    if (this.router.routerState.snapshot.url.endsWith('/new')) {
      this.createMode = true;
    }

    // filter default reporter type (mongo & jdbc) only additional reporters can be added
    this.plugins = this.route.snapshot.data['reporterPlugins'].filter(plugin => plugin.id !== 'mongodb' && plugin.id !== 'reporter-am-jdbc');

    if (!this.plugins) {
      this.plugins = [];
    }

    if (this.createMode) {
      this.reporter = {};
      this.reporterConfiguration = {};
      this.updateReporterConfiguration = this.reporterConfiguration;
      let reporterPlugin = this.plugins[0];

      this.reporter = {
        type: reporterPlugin.id,
        enabled: true
      }
    } else {
      this.reporter = this.route.snapshot.data['reporter'];
      this.reporterConfiguration = JSON.parse(this.reporter.configuration);
      this.updateReporterConfiguration = this.reporterConfiguration;
      this.validateName();
    }

    this.getSchemaFor(this.reporter.type);
  }

  getSchemaFor(type) {
    this.organizationService.reporterSchema(type).subscribe(data => {
      this.reporterSchema = data;

      // handle default null values
      let self = this;
      Object.keys(this.reporterSchema['properties']).forEach(function(key) {
        self.reporterSchema['properties'][key].default = '';
      });

      // for File reporter fill the filename entry with the domain
      if (this.reporter.type === 'reporter-am-file') {
        self.reporterSchema['properties']['filename'].default = this.domainId;
      }
    });
  }

  onReporterTypeChanged(event) {
    this.getSchemaFor(event.value);
  }

  labelFor(pluginId) {
    switch (pluginId) {
      case 'reporter-am-file':
        return "File";
      case 'reporter-am-kafka':
        return "Kafka";
      case 'reporter-am-jdbc':
        return "JDBC";
      case 'mongodb':
        return "MongoDB";
      default:
        return pluginId;
    }
  }

  save() {
    this.reporter.configuration = JSON.stringify(this.updateReporterConfiguration ? this.updateReporterConfiguration : {});
    if (this.createMode) {
      this.reporterService.create(this.domainId, this.reporter, this.organizationContext).subscribe(data => {
        this.reporter = data;
        let currentUrl = this.router.routerState.snapshot.url;
        this.router.navigateByUrl(currentUrl.substring(0, currentUrl.length -  3) +  this.reporter.id);
      });
    } else {
      this.reporterService.update(this.domainId, this.reporter.id, this.reporter, this.organizationContext).subscribe(data => {
        this.reporter = data;
        this.reporterConfiguration = JSON.parse(this.reporter.configuration);
        this.updateReporterConfiguration = this.reporterConfiguration;
        this.formChanged = false;
        this.form.reset(this.reporter);
        this.snackbarService.open('Reporter updated');
      });
    }
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Reporter', 'Are you sure you want to delete this reporter ?')
      .subscribe(res => {
        if (res) {
          this.reporterService.delete(this.domainId, this.reporter.id, this.organizationContext).subscribe(response => {
            this.snackbarService.open('Reporter ' + this.reporter.name + ' deleted');
            this.router.navigate(['..'], { relativeTo: this.route });
          })
        }
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

  validateName() {
    this.hasName = (this.reporter.name !== '' && this.reporter.name.trim() !== '');
  }

  isDefaultReporter() {
    return this.reporter.type === 'mongodb' || this.reporter.type === 'reporter-am-jdbc';
  }
}
