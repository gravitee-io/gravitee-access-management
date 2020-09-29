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
import {NgForm} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../../services/snackbar.service';
import {EntrypointService} from '../../../../services/entrypoint.service';
import {DialogService} from '../../../../services/dialog.service';
import {AuthService} from '../../../../services/auth.service';
import {Tag} from "../../../../domain/settings/general/general.component";
import * as _ from 'lodash';
import {MatInput} from "@angular/material/input";

@Component({
  selector: 'app-entrypoint',
  templateUrl: './entrypoint.component.html',
  styleUrls: ['./entrypoint.component.scss']
})
export class EntrypointComponent implements OnInit {
  entrypoint: any;
  @ViewChild('entrypointForm') public entrypointForm: NgForm;
  @ViewChild('chipInput') chipInput: MatInput;
  formChanged = false;
  readonly: boolean;
  tags: Tag[];
  selectedTags: Tag[];

  constructor(private entrypointService: EntrypointService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router,
              private dialogService: DialogService,
              private authService: AuthService) { }

  ngOnInit() {
    this.entrypoint = this.route.snapshot.data['entrypoint'];
    this.readonly = !this.authService.hasPermissions(['organization_entrypoint_update']);
    this.initTags();
  }

  initTags() {
    this.tags = this.route.snapshot.data['tags'];
    this.selectedTags = this.entrypoint.tags.map(t => _.find(this.tags, { 'id': t })).filter(t => typeof t !== 'undefined');
    this.tags = _.difference(this.tags, this.selectedTags);
  }

  addTag(event) {
    this.selectedTags = this.selectedTags.concat(_.remove(this.tags, { 'id': event.option.value }));
    this.tagsChanged();
  }

  removeTag(tag) {
    this.selectedTags = this.selectedTags.filter(t => t.id !== tag.id);
    this.tags.push(tag);
    this.tagsChanged();
  }

  tagsChanged() {
    this.chipInput['nativeElement'].blur();
    this.formChanged = true;
    this.entrypoint.tags = _.map(this.selectedTags, tag => tag.id);
  }

  update() {
    this.entrypointService.update(this.entrypoint.id, this.entrypoint).subscribe(data => {
      this.entrypoint = data;
      this.entrypointForm.reset(Object.assign({}, this.entrypoint));
      this.snackbarService.open('Entrypoint updated');
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Entrypoint', 'Are you sure you want to delete this entrypoint ?')
      .subscribe(res => {
        if (res) {
          this.entrypointService.delete(this.entrypoint.id).subscribe(response => {
            this.snackbarService.open('Entrypoint deleted');
            this.router.navigate(['/settings', 'entrypoints']);
          });
        }
      });
  }

  canDelete() {
    return !this.entrypoint.defaultEntrypoint && this.authService.hasPermissions(['organization_entrypoint_delete'])
  }
}
