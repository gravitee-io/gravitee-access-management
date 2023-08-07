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
import { Component, ElementRef, HostListener, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { ProviderService } from '../../../../../services/provider.service';
import { DialogService } from '../../../../../services/dialog.service';
import { OrganizationService } from '../../../../../services/organization.service';
import '@gravitee/ui-components/wc/gv-expression-language';

@Component({
  selector: 'provider-mappers',
  templateUrl: './mappers.component.html',
  styleUrls: ['./mappers.component.scss'],
})
export class ProviderMappersComponent implements OnInit {
  private domainId: string;
  private organizationContext = false;
  private defaultMappers: any = {
    sub: 'uid',
    email: 'mail',
    name: 'displayname',
    given_name: 'givenname',
    family_name: 'sn',
  };
  mappers: any = [];
  provider: any;
  editing = {};
  spelGrammar: any;

  constructor(
    private providerService: ProviderService,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private route: ActivatedRoute,
    private router: Router,
    private dialog: MatDialog,
    private organizationService: OrganizationService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.provider = this.route.snapshot.data['provider'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
    if (!this.provider.mappers) {
      this.setMappers(this.defaultMappers);
    } else {
      this.setMappers(this.provider.mappers);
    }
  }

  getGrammar() {
    if (this.spelGrammar != null) {
      return Promise.resolve(this.spelGrammar);
    }

    return this.organizationService
      .spelGrammar()
      .toPromise()
      .then((response) => {
        this.spelGrammar = response;
        return this.spelGrammar;
      });
  }

  setMappers(mappers) {
    this.mappers = [];
    for (const k in mappers) {
      if (mappers.hasOwnProperty(k)) {
        this.mappers.push({
          key: k,
          value: mappers[k],
        });
      }
    }
  }

  add() {
    const dialogRef = this.dialog.open(CreateMapperComponent, { width: '700px' });
    dialogRef.afterClosed().subscribe((mapper) => {
      if (mapper) {
        if (!this.attributeExits(mapper.key)) {
          this.mappers.push(mapper);
          this.mappers = [...this.mappers];
          this.update('Attribute added');
        } else {
          this.snackbarService.open(`Error : attribute ${mapper.key} already exists`);
        }
      }
    });
  }

  update(message) {
    this.provider.configuration = this.provider.configuration ? JSON.parse(this.provider.configuration) : {};
    this.provider.mappers = this.mappers.reduce(function (map, obj) {
      map[obj.key] = obj.value;
      return map;
    }, {});
    this.providerService.update(this.domainId, this.provider.id, this.provider, this.organizationContext).subscribe(() => {
      this.snackbarService.open(message);
    });
  }

  updateMapper(event, cell, rowIndex) {
    const mapper = event.target.value;
    if (mapper) {
      if (cell === 'key' && this.attributeExits(mapper)) {
        this.snackbarService.open(`Error : mapper ${mapper} already exists`);
        return;
      }

      this.editing[rowIndex + '-' + cell] = false;
      this.mappers[rowIndex][cell] = mapper;
      this.mappers = [...this.mappers];
      this.update('Mapper saved');
    }
  }

  delete(key, event) {
    event.preventDefault();
    this.dialogService.confirm('Delete Mapper', 'Are you sure you want to delete this mapper ?').subscribe((res) => {
      if (res) {
        this.mappers = this.mappers.filter(function (el) {
          return el.key !== key;
        });
        this.update('Mapper deleted');
      }
    });
  }

  attributeExits(attribute): boolean {
    return (
      this.mappers
        .map(function (m) {
          return m.key;
        })
        .indexOf(attribute) > -1
    );
  }

  get isEmpty() {
    return !this.mappers || this.mappers.length === 0;
  }

  @HostListener(':gv-expression-language:ready', ['$event.detail'])
  setGrammar({ currentTarget }) {
    this.getGrammar().then((grammar) => {
      currentTarget.grammar = grammar;
      currentTarget.requestUpdate();
    });
  }
}

@Component({
  selector: 'create-mapper',
  templateUrl: './create/create.component.html',
})
export class CreateMapperComponent {
  spelGrammar: any;
  rule: string;

  constructor(
    public dialogRef: MatDialogRef<CreateMapperComponent>,
    private elementRef: ElementRef,
    private organizationService: OrganizationService,
  ) {}

  ngOnInit() {
    this.organizationService
      .spelGrammar()
      .toPromise()
      .then((response) => {
        this.spelGrammar = response;
      });
  }

  getGrammar() {
    if (this.spelGrammar != null) {
      return Promise.resolve(this.spelGrammar);
    }

    return this.organizationService
      .spelGrammar()
      .toPromise()
      .then((response) => {
        this.spelGrammar = response;
        return this.spelGrammar;
      });
  }

  @HostListener(':gv-expression-language:ready', ['$event.detail'])
  setGrammar({ currentTarget }) {
    this.getGrammar().then((grammar) => {
      currentTarget.grammar = grammar;
      currentTarget.requestUpdate();
    });
  }

  change($event) {
    this.rule = $event.target.value;
  }
}
