import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { tap } from 'rxjs/operators';
import { toSequence } from './detail/detail.component';
import { SequenceData } from './flow-sequence/flow-sequence.component';
import { Action, isAction } from './seq-action/seq-action.component';
import { isFlow } from './types';

@Injectable({
  providedIn: 'root'
})
export class BasisFetchService {

  readonly start = "// START_JSON_DATA";
  readonly end = "// END_JSON_DATA";

  private basis?: SequenceData = undefined;

  private callbacks: (() => void)[] = [];

  constructor(private http: HttpClient) {
  }

  /**
   * Invoked when the detail json is decoded
   * @param detail The address of the basis flow
   */
  get(detail: string | undefined): void {
    if (detail != undefined) {
      let resp = this.http.get(detail + ".html",
        { responseType: "text" })
        .pipe(tap(
          body => console.log("fetched " + body.length + " bytes of basis content"),
          error => console.error("Failed to get ", error)
        ));

      resp.subscribe(page => {
        // extract the json content
        let s = page.indexOf(this.start);
        let e = page.indexOf(this.end, s);
        if (s != undefined && e != undefined) {
          // parse it
          let json = page.substring(s + this.start.length, e);
          let data = JSON.parse(json);
          if (isFlow(data)) {
            // save the flow
            this.basis = toSequence(data);
            // notify listeners
            this.callbacks.forEach(cb => cb());
          }
          else {
            console.error("This isn't a flow!", data);
          }
        }
        else {
          console.error("No json content found!");
        }
      });
    }
  }

  /**
   * @param callback This will be called when the basis data is available
   */
  onLoad(callback: () => void) {
    this.callbacks.push(callback);
  }

  /**
   * Invoked when a message is displayed
   * @param action The selected message in the current flow
   * @returns  The human-readable text of the corresponding message in the basis
   *           flow, or null if there is no such message
   */
  message(action: Action): string | null {
    // find the actions from the basis that are between the same actors
    // and that are of the same type (i.e.: request or response)
    let possibles = this.basis?.item
      .filter(isAction)
      .filter(a => action.fromName === a.fromName
        && action.toName === a.toName
        && action.label === a.label)
      ?? [];

    // oh there were none? Nevermind then!
    if (possibles.length == 0) {
      return null;
    }

    // now try and find the closest tag match
    let bestIdx = -1;
    let bestTagMatch = -1;
    for (let idx = 0; idx < possibles.length; idx++) {
      const e = possibles[idx];
      let tagMatch = intersectionSize(action.tags, e.tags);
      if (tagMatch > bestTagMatch) {
        bestIdx = idx;
        bestTagMatch = tagMatch; 
      }
    }

    return possibles[bestIdx]?.transmission?.full.expect ?? null;
  }

}

function intersectionSize(a: string[], b: string[]) {
  return a.filter(b.includes).length;
}
