import { Component, OnInit, Input, inject } from '@angular/core';
import { Action, isAction } from '../seq-action/seq-action.component';
import { Note, isNote } from '../seq-note/seq-note.component';
import { Section, isSection } from '../seq-section/seq-section.component';
import { TxSelectionService } from '../tx-selection.service';

@Component({
  selector: 'app-flow-sequence',
  templateUrl: './flow-sequence.component.html',
  styleUrls: ['./flow-sequence.component.css']
})
export class FlowSequenceComponent implements OnInit {
  private txSelect = inject(TxSelectionService);


  @Input() sequence: SequenceData = { entity: [], exercised: [], item: [] };

  ngOnInit(): void {
  }

  /* used in template, delegating to exported function */
  columnStyle(pos: number, count: number): Object {
    return columnStyle(pos, count);
  }
  /* used in template, delegating to exported function */
  leftStyle(pos: number, count: number): Object {
    return leftStyle(pos, count);
  }
  /* used in template, delegating to exported function */
  isAction(i: any): i is Action {
    return isAction(i);
  }
  /* used in template, delegating to exported function */
  isNote(i: any): i is Note {
    return isNote(i);
  }
  isSection(data: any): data is Section {
    return isSection(data);
  }
}

/**
 * Defines a sequence diagram
 */
export interface SequenceData {
  /** The column headers in the diagram */
  entity: string[];
  /** The column headers that are in the system under test */
  exercised: string[];
  /** The things that happen in the sequence */
  item: Array<Action | Note | Section>;
}

/**
 * @param pos The column index
 * @param count  The number of columns
 * @returns The style data to apply to things on the column
 */
export function columnStyle(pos: number, count: number): Object {
  return {
    "margin-left": percentage(pos / count),
    "margin-right": percentage((count - pos - 1) / count)
  };
}

/**
 * @param pos The column index
 * @param count The number of columns
 * @returns The style data to apply to things that extends to the right of the column
 */
export function leftStyle(pos: number, count: number): Object {
  return { "margin-left": percentage((pos + 0.5) / count) };
}

/**
 * @param pos The column index
 * @param count The number of columns
 * @returns The style data to apply to things that extends to the left of the column
 */
export function rightStyle(pos: number, count: number): Object {
  return { "margin-right": percentage((count - pos - 0.5) / count) };
}

/**
 * @param ratio A number in range 0-1
 * @returns The input ration, but as a percentage string to 1 decimal place
 */
function percentage(ratio: number): string {
  return "calc(" + (Math.round(ratio * 1000) / 10) + "% - 1px )";
}