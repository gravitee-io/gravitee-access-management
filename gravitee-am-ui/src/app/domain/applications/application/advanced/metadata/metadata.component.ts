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
import {ActivatedRoute} from "@angular/router";
import {ApplicationService} from "../../../../../services/application.service";
import {SnackbarService} from "../../../../../services/snackbar.service";
import * as _ from 'lodash';
import {NgForm} from "@angular/forms";

@Component({
  selector: 'app-application-metadata',
  templateUrl: './metadata.component.html',
  styleUrls: ['./metadata.component.scss']
})
export class ApplicationMetadataComponent implements OnInit {
  @ViewChild('metadataForm') public form: NgForm;
  private domainId: string;
  application: any;
  metadata: any = {};
  editing = {};
  appMetadata: any[] = [];
  formChanged: boolean = false;

  constructor(private route: ActivatedRoute,
              private applicationService: ApplicationService,
              private snackbarService: SnackbarService) { }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.params['domainId'];
    this.application = this.route.snapshot.data['application'];
    this.initMetadata();
  }

  initMetadata() {
    if (this.application.metadata) {
      _.forEach(this.application.metadata, (v, k) => {
        const metadata = {};
        metadata['id'] = Math.random().toString(36).substring(7);
        metadata['name'] = k;
        metadata['value'] = v;
        this.appMetadata.push(metadata);
      });
    }
  }

  addMetadata(event) {
    event.preventDefault();
    if (!this.metadataExits(this.metadata.name)) {
      this.metadata.id = Math.random().toString(36).substring(7);
      this.appMetadata.push(this.metadata);
      this.appMetadata = [...this.appMetadata];
      this.formChanged = true;
      this.metadata = {};
      this.form.reset(this.metadata);
    } else {
      this.snackbarService.open(`Error : metadata "${this.metadata.name}" already exists`);
    }
  }

  updateMetadata(event, cell, rowIndex) {
    let metadata = event.target.value;
    if (metadata) {
      if (cell === 'name' && this.metadataExits(metadata)) {
        this.snackbarService.open(`Error : metadata "${metadata}" already exists`);
        return;
      }
      this.editing[rowIndex + '-' + cell] = false;
      let index = _.findIndex(this.appMetadata, {id: rowIndex});
      this.appMetadata[index][cell] = metadata;
      this.appMetadata = [...this.appMetadata];
      this.formChanged = true;
    }
  }

  deleteMetadata(key, event) {
    event.preventDefault();
    _.remove(this.appMetadata, function(el) {
      return el.id === key;
    });
    this.appMetadata = [...this.appMetadata];
    this.formChanged = true;
  }

  metadataExits(attribute): boolean {
    return _.find(this.appMetadata, function(el) { return  el.name === attribute; })
  }

  metadataIsEmpty() {
    return !this.appMetadata || Object.keys(this.appMetadata).length === 0;
  }

  patch(): void {
    let metadata = {};
    _.each(this.appMetadata, function(item) {
      metadata[item.name] = item.value;
    });
    this.applicationService.patch(this.domainId, this.application.id, { 'metadata' : metadata }).subscribe(data => {
      this.application = data;
      this.route.snapshot.data['application'] = this.application;
      this.formChanged = false;
      this.snackbarService.open('Application updated');
      this.appMetadata = [];
      this.initMetadata();
    });
  }
}
