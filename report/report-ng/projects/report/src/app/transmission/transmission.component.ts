import { Component, OnInit, OnChanges, Input, ViewEncapsulation } from '@angular/core';
import { DiffContent, DiffTableFormat } from 'ngx-text-diff/lib/ngx-text-diff.model';
import { Observable, Subject } from 'rxjs';
import { DiffType, Display, Options } from '../types';
import { TxSelectionService } from '../tx-selection.service';
import { Transmission } from '../types';
import { QueryService } from '../query.service';
import { BasisFetchService } from '../basis-fetch.service';
import { Action, empty_action } from '../seq-action/seq-action.component';

@Component({
  selector: 'app-transmission',
  templateUrl: './transmission.component.html',
  styleUrls: ['./transmission.component.css'],
  encapsulation: ViewEncapsulation.None
})
export class TransmissionComponent implements OnInit, OnChanges {
  action?: Action;
  @Input() options: Options = new Options();

  // We don't stictly need these inputs, as we already have the options
  // input that contains them, but we do need to listen to changes on
  // these values so that ngOnChanges gets called
  @Input() display: Display = Display.Actual;
  @Input() diffType: DiffType = DiffType.Asserted;
  @Input() diffFormat: DiffTableFormat = 'LineByLine';

  // If the user want to show actual or diff data but that isn't
  // available, we'll show the expected data with a warning instead
  effectiveDisplay: Display = this.display;
  substitutionWarning: boolean = false;

  diffObservable: Subject<DiffContent> = new Subject<DiffContent>();
  diffObservable$: Observable<DiffContent> = this.diffObservable.asObservable();

  constructor(
    private txSelect: TxSelectionService,
    private query: QueryService,
    private basis: BasisFetchService) {
    txSelect.onSelected((action) => {
      this.action = action;
      this.ngOnChanges();
    });
    basis.onLoad(() => { this.refreshDiff(); });
  }

  ngOnInit(): void {
    this.action = this.txSelect.get();
  }

  ngOnChanges(): void {
    this.refreshDiff();
    if (this.display !== Display.Actual) {
      this.query.set("display", this.display.toString());
    }
    else {
      this.query.delete("display");
    }
    if (this.diffType !== DiffType.Asserted) {
      this.query.set("diff", this.diffType.toString());
    }
    else {
      this.query.delete("diff");
    }
    if (this.diffFormat !== 'LineByLine') {
      this.query.set("dtf", this.diffFormat);
    }
    else {
      this.query.delete("dtf");
    }

    this.effectiveDisplay = this.display;
    this.substitutionWarning = false;

    if (this.effectiveDisplay === Display.Diff && !this.hasDiffData()) {
      this.effectiveDisplay = Display.Expected;
      this.substitutionWarning = true;
    }
    if (this.effectiveDisplay === Display.Actual && this.action?.transmission.full.actualBytes === null) {
      this.effectiveDisplay = Display.Expected;
      this.substitutionWarning = true;
    }
  }

  /**
   * The diff component needs a wee kick to recalculate
   * itself - you can't just use the left and right inputs
   */
  refreshDiff(): void {
    const diff: DiffContent = {
      leftContent: this.diffLeft(),
      rightContent: this.diffRight(),
    }
    this.diffObservable.next(diff);
  }

  diffLeft(): string {
    if (this.options.display === Display.Basis) {
      return this.basis.message(this.action || empty_action) ?? '';
    }
    return this.options.diffType === DiffType.Full
      ? this.action?.transmission?.full?.expect ?? ''
      : this.action?.transmission?.asserted?.expect ?? '';
  }

  diffRight(): string {
    if (this.options.display === Display.Basis) {
      return this.action?.transmission?.full?.expect ?? '';
    }
    return this.options.diffType === DiffType.Full
      ? this.action?.transmission?.full?.actual ?? ''
      : this.action?.transmission?.asserted?.actual ?? '';
  }

  hasDiffData(): boolean {
    if (this.options.display === Display.Basis) {
      return true;
    }
    return this.options.diffType === DiffType.Full
      ? this.action?.transmission?.full?.actual != null
      : this.action?.transmission?.asserted?.actual != null;
  }
}
