import { Component, OnInit, Input } from '@angular/core';
import { BasisFetchService } from '../basis-fetch.service';
import { leftStyle, rightStyle } from '../flow-sequence/flow-sequence.component';
import { MsgSearchService } from '../msg-search.service';
import { TxSelectionService } from '../tx-selection.service';
import { Transmission, isTransmission, empty_transmission } from '../types';
import { Diff, diff_match_patch } from 'diff-match-patch';

@Component({
  selector: 'app-seq-action',
  templateUrl: './seq-action.component.html',
  styleUrls: ['./seq-action.component.css']
})
export class SeqActionComponent implements OnInit {
  private readonly dmp: diff_match_patch = new diff_match_patch();

  @Input() entity: string[] = ["empty"];
  @Input() action: Action = empty_action;
  title: string = "";
  style: Object = {};
  selected: boolean = false;
  hasActual: boolean = false;
  hasBasis: boolean = false;
  assertionPassed: boolean = false;
  assertionFailed: boolean = false;
  assertionCoverage: string = "";

  markExpected: boolean = false;
  markActual: boolean = false;

  constructor(
    private txSelect: TxSelectionService,
    private basis: BasisFetchService,
    private search: MsgSearchService) {

    txSelect.onSelected(tx => this.selected = tx === this.action);
    basis.onLoad(() => this.hasBasis = this.basis.message(this.action) != null);

    search.onClear(() => {
      this.markActual = false;
      this.markExpected = false;
    });
  }

  ngOnInit(): void {
    this.selected = this.txSelect.get() === this.action;
    this.hasActual = (this.action.transmission.full.actualBytes ?? null) != null;
    this.hasBasis = this.basis.message(this.action) != null;

    this.style = {
      ...leftStyle(Math.min(this.action.from, this.action.to), this.entity.length),
      ...rightStyle(Math.max(this.action.from, this.action.to), this.entity.length),
    };
    this.title = this.action.from < this.action.to
      ? this.entity[this.action.from] + "->" + this.entity[this.action.to]
      : this.entity[this.action.to] + "<-" + this.entity[this.action.from];

    this.assertionPassed = this.action.transmission.asserted?.actual !== null
      && this.action.transmission.asserted?.actual === this.action.transmission.asserted?.expect;
    this.assertionFailed = this.hasActual && !this.assertionPassed;

    // full.expect is the raw message from the system model, asserted.expect is
    // the same message after masking operations have been applied. The coverage
    // metric gives an indication of how much those two differ, which can be taken
    // as an indication of test strength. 100% means that no masking has taken
    // place - the test is strong. 0% indicates that masking has completely
    // transformed the message - the test is weak
    this.assertionCoverage = this.computeCoverage(
      this.action.transmission.full.expect,
      this.action.transmission.asserted?.expect);

    this.search.onSearch(term => {
      this.markExpected = this.action.transmission.full.expect.indexOf(term) >= 0;
      this.markActual = (this.action.transmission.full.actual ?? "").indexOf(term) >= 0;
    });
  }

  displayTransmission() {
    this.txSelect.selected(this.action);
  }

  class(): string[] {
    let classes: string[] = [];
    if (this.selected) {
      classes.push('selected');
    }
    if (this.assertionPassed) {
      classes.push("pass");
    }
    if (this.assertionFailed) {
      classes.push("fail");
    }

    return classes;
  }

  computeCoverage(full: string, asserted: string | undefined): string {
    if (asserted === undefined || asserted === null) {
      return "";
    }

    let diffs: Diff[] = this.dmp.diff_main(full, asserted);
    this.dmp.diff_cleanupSemantic(diffs);

    // levenshtein distance is the number of changed characters - minimum
    // is 0, maximum is the length of the longer input string
    const levenshtein: number = this.dmp.diff_levenshtein(diffs);
    const max = Math.max(full.length, asserted.length);

    // using floor to avoid the false confidence a 100% result
    // would give when the diff is actually non-zero
    return Math.floor(100.0 * (1.0 - (levenshtein / max))) + "%";
  }
}

/**
 * An action in the sequence diagram
 */
export interface Action {
  /** The index of the action in the flow */
  index: number;
  /** The source of the action. An index into the entity list */
  from: number;
  fromName: string;
  /** The destination of the action. An index into the entity list */
  to: number;
  toName: string;
  /** A label for the action */
  label: string;
  /** action tags */
  tags: string[];
  /** The message data */
  transmission: Transmission;
}

/**
 * Type guard to turn arbitrary data into our Action type
 * @param data A data structure
 * @returns  true if the data matches the Action structure
 */
export function isAction(data: any): data is Action {
  return data
    && data.index != null
    && typeof data.index === 'number'
    && data.from != null
    && typeof data.from === 'number'
    && data.fromName != null
    && typeof data.fromName === 'string'
    && data.to != null
    && typeof data.to === 'number'
    && data.toName != null
    && typeof data.toName === 'string'
    && data.label != null
    && typeof data.label === 'string'
    && Array.isArray(data.tags)
    && data.tags.every((i: any) => typeof i === 'string')
    && data.transmission != null
    && isTransmission(data.transmission);
}

export const empty_action: Action = {
  index: 0,
  from: 0,
  fromName: '',
  to: 0,
  toName: '',
  label: "",
  tags: [],
  transmission: empty_transmission
};
