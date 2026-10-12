import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { IndexDataService } from './index-data.service';
import { FlowLoadStatus, ModelDiffDataService } from './model-diff-data.service';
import { empty_flow, Index } from './types';

describe('ModelDiffDataService flow settlement', () => {
  let service: ModelDiffDataService;
  let http: HttpTestingController;
  let indexData: jasmine.SpyObj<IndexDataService>;
  let statuses: FlowLoadStatus[];

  beforeEach(() => {
    indexData = jasmine.createSpyObj(['get']);
    statuses = [];
    TestBed.configureTestingModule({
      providers: [
        ModelDiffDataService,
        { provide: IndexDataService, useValue: indexData },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ModelDiffDataService);
    http = TestBed.inject(HttpTestingController);
    service.onFlowLoadStatus('this', (label, status) => statuses.push(status));
  });

  afterEach(() => http.verify());

  it('settles only after every successful Detail response', () => {
    indexData.get.and.returnValue(index('a', 'b'));
    service.path('this', '');

    http.expectOne('detail/a.html').flush(page(empty_flow));
    expect(lastStatus()).toEqual(status(2, 1, 0));

    http.expectOne('detail/b.html').flush(page(empty_flow));
    expect(lastStatus()).toEqual(status(2, 2, 0));
  });

  it('counts failed and malformed Detail responses as settled', () => {
    indexData.get.and.returnValue(index('a', 'b'));
    service.path('this', '');

    http.expectOne('detail/a.html').flush('not a report page');
    expect(lastStatus()).toEqual(status(2, 1, 1));

    http.expectOne('detail/b.html').flush('failed', { status: 500, statusText: 'Failed' });
    expect(lastStatus()).toEqual(status(2, 2, 2));
  });

  it('settles an empty Index immediately', () => {
    indexData.get.and.returnValue(index());
    service.path('this', '');

    expect(lastStatus()).toEqual(status(0, 0, 0));
  });

  it('bounds concurrent Detail requests when requested', () => {
    indexData.get.and.returnValue(index('a', 'b', 'c'));
    service.path('this', '', 2);

    const first = http.expectOne('detail/a.html');
    const second = http.expectOne('detail/b.html');
    http.expectNone('detail/c.html');

    first.flush(page(empty_flow));
    const third = http.expectOne('detail/c.html');
    second.flush(page(empty_flow));
    third.flush(page(empty_flow));

    expect(lastStatus()).toEqual(status(3, 3, 0));
  });

  function lastStatus(): FlowLoadStatus {
    return statuses[statuses.length - 1];
  }
});

function index(...details: string[]): Index {
  return {
    meta: { modelTitle: '', testTitle: '', timestamp: 0 },
    entries: details.map(detail => ({ description: detail, tags: [], detail })),
  };
}

function page(flow: object): string {
  return `// START_JSON_DATA\n${JSON.stringify(flow)}\n// END_JSON_DATA`;
}

function status(total: number, settled: number, failed: number): FlowLoadStatus {
  return {
    total,
    settled,
    failed,
    complete: settled === total,
    progress: total === 0 ? 100 : 100 * settled / total,
  };
}
