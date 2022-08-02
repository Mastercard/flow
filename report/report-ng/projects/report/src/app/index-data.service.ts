import { Injectable } from '@angular/core';
import { empty_index, Index, isIndex } from './types';

@Injectable({
  providedIn: 'root'
})
export class IndexDataService {

  raw: any = {};
  index: Index = empty_index;
  valid: boolean = false;
  constructor() { }

  set(data: any) {
    this.raw = data;
    this.valid = false;
    this.index = empty_index;
    if (isIndex(this.raw)) {
      this.index = this.raw;
      this.valid = true;
    }
  }

  get(): Index {
    return this.index;
  }

  isValid(): boolean {
    return this.valid;
  }
}
