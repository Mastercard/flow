import { Component, OnInit, Input } from '@angular/core';
import { Action, isAction } from '../seq-action/seq-action.component';
import { Note, isNote } from '../seq-note/seq-note.component';

@Component({
  selector: 'app-seq-section',
  templateUrl: './seq-section.component.html',
  styleUrls: ['./seq-section.component.css']
})
export class SeqSectionComponent implements OnInit {

  @Input() entity: string[] = ["empty"];
  @Input() section: Section = { title: "", item: [] };

  ngOnInit(): void {
  }

  /* used in template, delegating to exported function */
  isAction(i: any): i is Action {
    return isAction(i);
  }
  /* used in template, delegating to exported function */
  isNote(i: any): i is Note {
    return isNote(i);
  }
}

/** A non-nestable group of items */
export interface Section {
  /** A label for the section */
  title: string
  /** section contents */
  item: Array<Action | Note>;
}

/**
 * Type guard to turn arbitrary data into a Section
 * @param data a data structure
 * @returns true if the data structure can be treated as a Section 
 */
export function isSection(data: any): data is Section {
  return data
    && data.title != null
    && typeof data.title === 'string'
    && Array.isArray(data.item)
    && data.item.every((i: any) => isAction(i) || isNote(i));
}
