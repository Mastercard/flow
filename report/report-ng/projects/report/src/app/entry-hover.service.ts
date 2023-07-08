import { Injectable } from '@angular/core';
import { Entry } from './types';

/**
 * Mechanism for watching which index entries are hovered over
 */
@Injectable({
  providedIn: 'root'
})
export class EntryHoverService {

  listeners: ((entry: Entry | null) => void)[] = [];

  constructor() { }

  onHover(callback: (entry: Entry | null) => void): void {
    this.listeners.push(callback);
  }

  hovered(entry: Entry | null): void {
    this.listeners.forEach(cb => cb(entry));
  }
}
