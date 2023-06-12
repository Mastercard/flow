import { TestBed } from '@angular/core/testing';

import { IconEmbedService } from './icon-embed.service';

describe('IconEmbedService', () => {
  let service: IconEmbedService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(IconEmbedService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
