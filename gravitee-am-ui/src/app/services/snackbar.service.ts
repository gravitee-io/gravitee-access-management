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
import { Injectable } from "@angular/core";
import { MatSnackBar, MatSnackBarConfig } from "@angular/material/snack-bar";
import { SnackbarComponent } from "../components/snackbar/snackbar.component";

@Injectable()
export class SnackbarService {

  constructor(private snackBar: MatSnackBar) { }

  open(message: string) {
    let config = new MatSnackBarConfig();
    config.duration = 1500;
    this.snackBar.open(message, '', config);
  }

  openFromComponent(title: string, errors: any) {
    let config = new MatSnackBarConfig();
    config.duration = 3000;
    let snackBarRef = this.snackBar.openFromComponent(SnackbarComponent, config);
    snackBarRef.instance.title = title;
    snackBarRef.instance.errors = errors
  }
}
