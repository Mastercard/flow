import { TestBed } from '@angular/core/testing';

import { EntryHoverService } from './entry-hover.service';

describe('EntryHoverService', () => {
  let service: EntryHoverService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(EntryHoverService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
