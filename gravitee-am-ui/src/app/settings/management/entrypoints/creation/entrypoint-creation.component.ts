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
import {Component} from '@angular/core';
import {ActivatedRoute, Router} from "@angular/router";
import {SnackbarService} from "../../../../services/snackbar.service";
import {EntrypointService} from "../../../../services/entrypoint.service";

@Component({
  selector: 'app-creation',
  templateUrl: './entrypoint-creation.component.html',
  styleUrls: ['./entrypoint-creation.component.scss']
})
export class EntrypointCreationComponent {
  entrypoint: any = { tags: [] };

  constructor(private entrypointService: EntrypointService, private router: Router, private route: ActivatedRoute,
              private snackbarService : SnackbarService) { }

  create() {
    this.entrypointService.create(this.entrypoint).subscribe(data => {
      this.snackbarService.open("Entrypoint " + data.name + " created");
      this.router.navigate(['/settings', 'entrypoints', data.id]);
    });
  }

}
