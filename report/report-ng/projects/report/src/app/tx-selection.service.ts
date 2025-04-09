import { Injectable, inject } from '@angular/core';
import { QueryService } from './query.service';
import { Action, empty_action } from './seq-action/seq-action.component';
import { empty_transmission, Transmission } from './types';

/**
 * Linkage between the sequence diagram actions (which get clicked)
 * and the transmission view (which displays the clicked transmission)
 */
@Injectable({
  providedIn: 'root'
})
export class TxSelectionService {
  private query = inject(QueryService);

  private current: Action = empty_action;
  private callbacks: ((a: Action) => void)[] = [];

  get(): Action {
    return this.current;
  }

  selected(action: Action) {
    if (action.index !== 0) {
      this.query.set("msg", action.index.toString());
    }
    else {
      this.query.delete("msg");
    }
    this.current = action;
    this.callbacks.forEach(cb => cb(action));
  }

  onSelected(callback: (a: Action) => void) {
    this.callbacks.push(callback);
  }
}
