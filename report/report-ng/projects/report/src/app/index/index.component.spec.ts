import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { IndexComponent } from './index.component';
import { IndexDataService } from '../index-data.service';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Entry } from '../types';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { FlowFilterService } from '../flow-filter.service';
import { ActivatedRoute, convertToParamMap, ParamMap } from '@angular/router';
import { Observable, of } from 'rxjs';
import { By } from '@angular/platform-browser';

describe('IndexComponent', () => {
  let component: IndexComponent;
  let fixture: ComponentFixture<IndexComponent>;
  let mockIndexData: jasmine.SpyObj<IndexDataService>;
  let mockRoute: { snapshot: { queryParamMap: ParamMap }, queryParamMap: Observable<ParamMap> };

  beforeEach(async () => {
    mockIndexData = jasmine.createSpyObj(['get', 'isValid', 'raw']);
    mockIndexData.get.and.returnValue({
      meta: {
        timestamp: 12345,
        modelTitle: "model title",
        testTitle: "test title",
      },
      entries: [],
    });
    mockIndexData.isValid.and.returnValue(true);
    const queryParamMap = convertToParamMap({});
    mockRoute = { snapshot: { queryParamMap }, queryParamMap: of(queryParamMap) };
    await TestBed.configureTestingModule({
      declarations: [
        IndexComponent,
        StubMenu,
        StubFlowFilter,
        StubTagSummary,
        StubSystemDiagram,
        StubFlowNavList,
      ],
      providers: [
        { provide: IndexDataService, useValue: mockIndexData },
        { provide: ActivatedRoute, useValue: mockRoute },
      ],
      imports: [
        MatPaginatorModule,
        MatToolbarModule,
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(IndexComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders and navigates one bounded page of flows', () => {
    const entries = Array.from({ length: 205 }, (_, index) => entry(index + 1));
    mockIndexData.get.and.returnValue(index(entries));
    fixture.destroy();
    fixture = TestBed.createComponent(IndexComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.pagedFlows.map(flow => flow.detail))
      .toEqual(entries.slice(0, 50).map(flow => flow.detail));
    expect(component.pageSize).toBe(50);

    component.onPage({ pageIndex: 1, pageSize: 50, length: 205 } as PageEvent);

    expect(component.pagedFlows.map(flow => flow.detail))
      .toEqual(entries.slice(50, 100).map(flow => flow.detail));
  });

  it('uses only the standard Material paginator controls and range', () => {
    mockIndexData.get.and.returnValue(index(Array.from({ length: 205 }, (_, i) => entry(i + 1))));
    fixture.destroy();
    fixture = TestBed.createComponent(IndexComponent);
    fixture.detectChanges();

    const pagination = fixture.debugElement.query(By.css('.pagination'));
    expect(pagination.query(By.css('.page-jump'))).toBeNull();
    expect(pagination.nativeElement.textContent).toContain('1 – 50 of 205');
  });

  it('hides the paginator when all matching Flows fit on the smallest page', () => {
    mockIndexData.get.and.returnValue(index(Array.from({ length: 50 }, (_, i) => entry(i + 1))));
    fixture.destroy();
    fixture = TestBed.createComponent(IndexComponent);
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('mat-paginator'))).toBeNull();

    mockIndexData.get.and.returnValue(index(Array.from({ length: 51 }, (_, i) => entry(i + 1))));
    fixture.destroy();
    fixture = TestBed.createComponent(IndexComponent);
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('mat-paginator'))).not.toBeNull();
  });

  it('resets to the first page and filters descriptions case-insensitively', () => {
    const entries = Array.from({ length: 205 }, (_, index) => entry(index + 1));
    mockIndexData.get.and.returnValue(index(entries));
    fixture.destroy();
    fixture = TestBed.createComponent(IndexComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.onPage({ pageIndex: 2, pageSize: 50, length: 205 } as PageEvent);

    TestBed.inject(FlowFilterService).setDescriptionMatch('flow 1');

    expect(component.pageIndex).toBe(0);
    expect(component.pagedFlows[0].detail).toBe('flow-1');
  });

  it('shows a clear empty-result state without a stale range', () => {
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('#no_flows')).nativeElement.textContent.trim())
      .toBe('No flows match the current filters.');
    expect(fixture.debugElement.query(By.css('mat-paginator'))).toBeNull();
  });
});

function entry(number: number): Entry {
  return {
    description: `Flow ${number}`,
    tags: ['PASS', `tag-${number}`],
    detail: `flow-${number}`,
  };
}

function index(entries: Entry[]) {
  return {
    meta: {
      timestamp: 12345,
      modelTitle: 'model title',
      testTitle: 'test title',
    },
    entries,
  };
}

@Component({
    selector: 'app-menu',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubMenu {
}
@Component({
    selector: 'app-flow-filter',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubFlowFilter {
  @Input() tags: Set<string> = new Set();
}
@Component({
    selector: 'app-tag-summary',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubTagSummary {
  @Input() entries: Entry[] = [];
}
@Component({
    selector: 'app-system-diagram',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubSystemDiagram {
}
@Component({
    selector: 'app-flow-nav-list',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubFlowNavList {
  @Input() entries: Entry[] = [];
  @Input() draggable: boolean = false;
}