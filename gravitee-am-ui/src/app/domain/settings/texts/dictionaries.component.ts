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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {DomainService} from '../../../services/domain.service';
import {DialogService} from '../../../services/dialog.service';
import {SnackbarService} from '../../../services/snackbar.service';
import {AuthService} from '../../../services/auth.service';
import {NavbarService} from '../../../components/navbar/navbar.service';
import {DictionaryDialog} from "../../../components/dialog/dictionary/dictionary-dialog.component";
import {MatDialog} from "@angular/material/dialog";
import {I18nDictionaryService} from "../../../services/dictionary.service";

@Component({
  selector: 'app-dictionaries',
  templateUrl: './dictionaries.component.html',
  styleUrls: ['./dictionaries.component.scss']
})
export class DomainSettingsDictionariesComponent implements OnInit {
  private envId: string;
  domain: any = {};
  readonly = false;
  dictionaries: any[] = [];
  selectedDictionaryEntries: any[] = [];
  selectedDictionary: any = {};

  constructor(private domainService: DomainService,
              private dialogService: DialogService,
              private dictionaryService: I18nDictionaryService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute,
              private authService: AuthService,
              private navbarService: NavbarService,
              private dialog: MatDialog) {
  }

  ngOnInit() {
    this.envId = this.route.snapshot.params['envHrid'];
    this.domain = this.route.snapshot.data['domain'];
    this.dictionaries = this.route.snapshot.data['dictionaries'];
    this.readonly = !this.authService.hasPermissions(['domain_i18n_dictionary_update']);
  }

  openDialog() {
    const dialogRef = this.dialog.open(DictionaryDialog,
      {
        data: {
          title: "Add a new language",
          prop1Label: "Language code",
          prop2Label: "Display name"
        }
      });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        const language = result.prop1;
        const name = result.prop2;
        this.dictionaryService.create(this.domain.id, {locale: language, name: name}).subscribe(created => {
          this.snackbarService.open('Language created');
          let tempDicts = [...this.dictionaries];
          tempDicts.push(created);
          this.dictionaries = tempDicts;
        });
      }
    });
  }

  deleteLanguage(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Language', 'Are you sure you want to delete this language?')
      .subscribe(res => {
        if (res) {
          this.dictionaryService.delete(this.domain.id, id).subscribe(() => {
            this.snackbarService.open('Language deleted');
            this.dictionaries = this.dictionaries.filter(dict => dict.id != id);
            if (this.selectedDictionary.id === id) {
              this.selectedDictionaryEntries = [];
            }
          });
        }
      });
  }

  deleteTranslation(rowIndex, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete translation', 'Are you sure you want to delete this translation?')
      .subscribe(res => {
        const deleteCount = 1;
        if (res) {
          this.selectedDictionaryEntries.splice(rowIndex, deleteCount);
        }
      });
  }

  changeTranslation() {
    this.selectedDictionaryEntries = [];

    Object.entries(this.selectedDictionary.entries).forEach(([k, v]) => {
      this.selectedDictionaryEntries.push({key: k, message: v});
    });
  }

  addTranslation() {
    let tempEntries = [...this.selectedDictionaryEntries];
    if (this.selectedDictionary.id) {
      const dialogRef = this.dialog.open(DictionaryDialog,
        {
          data: {
            title: "Add a new translation",
            prop1Label: "Key",
            prop2Label: "Value"
          }
        });

      dialogRef.afterClosed().subscribe(result => {
        const entryKey = result.prop1;
        const value = result.prop2;
        tempEntries.push({key: entryKey, message: value});
        this.selectedDictionaryEntries = tempEntries;
      });
    } else {
      this.snackbarService.open("Please select a language");
    }
  }

  saveChanges() {
    if (this.selectedDictionary.id) {
      // @ts-ignore
      const updateEntries = Object.fromEntries(this.selectedDictionaryEntries.flatMap(row => [Object.values(row)]));
      this.dictionaryService.update(this.domain.id, this.selectedDictionary.id,
        {entries: updateEntries}).subscribe(result => {
        if (result) {
          this.snackbarService.open("Translations saved");
          for (const dictionary of this.dictionaries) {
            if (dictionary.id === this.selectedDictionary.id) {
              dictionary.entries = updateEntries;
              break;
            }
          }
        }
      });
    } else {
      this.snackbarService.open("Please select a language");
    }
  }
}
