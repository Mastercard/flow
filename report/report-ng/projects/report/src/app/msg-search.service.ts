import { L } from '@angular/cdk/keycodes';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class MsgSearchService {

  constructor() { }

  private term: string = "";
  private clearListeners: (() => void)[] = [];
  private searchListeners: ((term: string) => void)[] = [];

  search(term: string): void {
    this.term = term;
    if (term.length === 0) {
      this.clearListeners.forEach(l => l());
    }
    else {
      this.searchListeners.forEach(l => l(this.term));
    }
  }

  onClear(callback: () => void): void {
    this.clearListeners.push(callback);

    if (this.term.length === 0) {
      callback();
    }
  }

  onSearch(callback: (term: string) => void): void {
    this.searchListeners.push(callback);

    if (this.term.length > 0) {
      callback(this.term);
    }
  }

  getTerm(): string {
    return this.term;
  }
}
