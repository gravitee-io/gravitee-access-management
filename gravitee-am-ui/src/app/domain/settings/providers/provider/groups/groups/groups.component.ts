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
import { Component, HostListener, Inject, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import { NgForm } from '@angular/forms';

import { SnackbarService } from '../../../../../../services/snackbar.service';
import { ProviderService } from '../../../../../../services/provider.service';
import { SpelGrammarService } from '../../../../../../services/spel-grammar.service';

interface MapperModel {
  groupId: string;
  users: string[];
}

interface GroupModel {
  id: string;
  name: string;
}

interface ProviderModel {
  id: string;
  groupMapper: any;
  configuration: any;
}

@Component({
  selector: 'app-groups',
  templateUrl: './groups.component.html',
  styleUrl: './groups.component.scss',
  standalone: false,
})
export class ProviderGroupsComponent implements OnInit {
  mappers: MapperModel[] = [];
  organizationContext: boolean;
  groups: Map<string, GroupModel>;
  provider: ProviderModel;
  domainId: string;

  constructor(
    private dialog: MatDialog,
    private route: ActivatedRoute,
    private providerService: ProviderService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.organizationContext = this.route.snapshot.data['organizationContext'];
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.provider = this.route.snapshot.data['provider'];
    this.groups = this.initGroupModel(this.route.snapshot.data['groups']);
    this.mappers = this.initMappersModel(this.provider);
  }

  private initGroupModel(routeData: any): Map<string, GroupModel> {
    if (routeData && routeData.data) {
      return routeData.data.reduce((map, item) => {
        map.set(item.id, item);
        return map;
      }, new Map<number, GroupModel>());
    } else {
      return new Map();
    }
  }

  private initMappersModel(provider: ProviderModel): MapperModel[] {
    if (provider && provider.groupMapper) {
      return Object.keys(provider.groupMapper).map((key) => {
        return {
          users: provider.groupMapper[key],
          groupId: key,
        } as MapperModel;
      });
    } else {
      return [];
    }
  }

  get isEmpty() {
    return this.mappers.length === 0;
  }

  add() {
    const inputData: InputData = {
      groups: Array.from(this.groups.values()),
    };
    const dialogRef = this.dialog.open(CreateGroupMapperComponent, {
      data: inputData,
      width: '700px',
    });

    dialogRef.afterClosed().subscribe((data: OutputData) => {
      if (data) {
        const errorMessages = [];
        let groupMapped = false;

        data.groups.forEach((groupId) => {
          const mappers = this.mappers.filter((mapper) => mapper.groupId === groupId);
          const exists = this.mappers.filter((mapper) => mapper.groupId === groupId).length > 0;
          if (!exists) {
            if (data.user) {
              this.mappers.push({
                groupId: groupId,
                users: [data.user],
              });
            }
            groupMapped = true;
          } else {
            mappers.forEach((mapper) => {
              if (data.user) {
                if (mapper.users.indexOf(data.user) === -1) {
                  mapper.users.push(data.user);
                  groupMapped = true;
                } else {
                  errorMessages.push(`'${data.user}' has already the group`);
                }
              }
            });
          }
        });

        if (groupMapped) {
          this.update();
        }

        if (errorMessages.length > 0) {
          this.snackbarService.openFromComponent('Errors', errorMessages);
        }
      }
    });
  }

  private update() {
    this.provider.configuration = this.provider.configuration ? JSON.parse(this.provider.configuration) : {};
    this.provider.groupMapper = {};
    this.mappers.forEach((mapper) => {
      this.provider.groupMapper[mapper.groupId] = mapper.users;
    });
    this.providerService.update(this.domainId, this.provider.id, this.provider, this.organizationContext).subscribe(() => {
      this.snackbarService.open('Group mapping updated');
    });
  }

  delete(mapper: MapperModel, user: string, event) {
    event.preventDefault();
    const mapperIdx = this.mappers.indexOf(mapper);
    const userIdx = mapper.users.indexOf(user);
    this.mappers[mapperIdx].users.splice(userIdx, 1);
    if (this.mappers[mapperIdx].users.length === 0) {
      this.mappers.splice(mapperIdx, 1);
    }
    this.update();
  }
}

interface InputData {
  groups: GroupModel[];
}

interface OutputData {
  user: string;
  groups: string[];
}

@Component({
  selector: 'create-group-mapper',
  templateUrl: './create/create.component.html',
  standalone: false,
})
export class CreateGroupMapperComponent implements OnInit {
  @ViewChild('userGroupForm', { static: true }) form: NgForm;
  rule: string = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: InputData,
    public dialogRef: MatDialogRef<CreateGroupMapperComponent>,
    private spelGrammarChecker: SpelGrammarService,
  ) {}

  ngOnInit() {
    this.spelGrammarChecker.init();
  }

  @HostListener(':gv-expression-language:ready', ['$event.detail'])
  setGrammar({ currentTarget }) {
    this.spelGrammarChecker.getGrammar().then((grammar) => {
      currentTarget.grammar = grammar;
      currentTarget.requestUpdate();
    });
  }

  get formInvalid() {
    const formValue = this.form.value;
    return !formValue.user;
  }

  change($event) {
    this.rule = $event.target.value;
  }

  save() {
    const result: OutputData = this.form.value;
    this.dialogRef.close(result);
  }

  cancel() {
    this.dialogRef.close();
  }
}
