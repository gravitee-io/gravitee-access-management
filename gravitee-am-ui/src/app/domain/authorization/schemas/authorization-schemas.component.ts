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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';

import { AuthorizationSchemaService } from '../../../services/authorization-schema.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';

@Component({
  selector: 'app-authorization-schemas',
  templateUrl: './authorization-schemas.component.html',
  styleUrls: ['./authorization-schemas.component.scss'],
  standalone: false,
})
export class AuthorizationSchemasComponent implements OnInit {
  schemas: any[];
  domainId: string;

  constructor(
    private authorizationSchemaService: AuthorizationSchemaService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.schemas = this.route.snapshot.data['schemas'] || [];
  }

  loadSchemas() {
    this.authorizationSchemaService.findByDomain(this.domainId).subscribe((schemas) => (this.schemas = schemas));
  }

  get isEmpty() {
    return !this.schemas || this.schemas.length === 0;
  }

  delete(id: string, event: any) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Authorization Schema', 'Are you sure you want to delete this authorization schema?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.authorizationSchemaService.delete(this.domainId, id)),
        tap(() => {
          this.snackbarService.open('Authorization schema deleted');
          this.loadSchemas();
        }),
      )
      .subscribe();
  }
}
