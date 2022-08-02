import { Component, OnInit, Input } from '@angular/core';
import { columnStyle } from '../flow-sequence/flow-sequence.component';

@Component({
  selector: 'app-seq-note',
  templateUrl: './seq-note.component.html',
  styleUrls: ['./seq-note.component.css']
})
export class SeqNoteComponent implements OnInit {

  @Input() entity: string[] = ["empty"];
  @Input() note: Note = { on: 0, note: "" };
  style: Object = {};

  ngOnInit(): void {
    this.style = columnStyle(this.note.on, this.entity.length);
  }

}

/** A note in the sequence diagram */
export interface Note {
  /** The entity that bears the note */
  on: number;
  /** The content of the note */
  note: string;
}

/**
 * Type guard to turn arbitrary data into our Note type
 * @param data A data structure
 * @returns  true if the data matches the Note structure
 */
export function isNote(data: any): data is Note {
  return data
    && data.on != null
    && typeof data.on === 'number'
    && data.note != null
    && typeof data.note === 'string';
}
