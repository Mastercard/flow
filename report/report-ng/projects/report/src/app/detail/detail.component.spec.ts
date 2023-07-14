import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input } from '@angular/core';
import { DetailComponent } from './detail.component';
import { BasisFetchService } from '../basis-fetch.service';
import { MatMenuModule } from '@angular/material/menu';
import { MatListModule } from '@angular/material/list';
import { DiffType, Display, LogEvent, Options, empty_flow, empty_interaction } from '../types';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { SequenceData } from '../flow-sequence/flow-sequence.component';
import { DiffDisplay } from '../text-diff/text-diff.component';

describe('DetailComponent', () => {
  let component: DetailComponent;
  let fixture: ComponentFixture<DetailComponent>;
  let mockBasisFetch;

  beforeEach(async () => {
    mockBasisFetch = jasmine.createSpyObj(['get']);
    await TestBed.configureTestingModule({
      declarations: [
        DetailComponent,

        StubLogView,
        StubFlowSequence,
        StubTransmission,
        StubMarkdown,
        StubViewOptions,
      ],
      providers: [
        { provide: BasisFetchService, useValue: mockBasisFetch },
      ],
      imports: [
        MatMenuModule,
        BrowserAnimationsModule,
        MatSidenavModule,
        MatIconModule,
        MatListModule,
        MatTabsModule,
        MatToolbarModule,
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(DetailComponent);
    component = fixture.componentInstance;
    component.flow = {
      description: "",
      tags: [],
      motivation: "",
      trace: "",
      dependencies: {},
      context: {},
      residue: [],
      root: empty_interaction,
      logs: [],
    };
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have no tabs with no logs, context or residue', () => {
    expect(testTabs(component, fixture, {
    }))
      .toEqual([]);
  });

  it('should have two tabs with logs', () => {
    expect(testTabs(component, fixture, {
      "logs": [{}]
    }))
      .toEqual(["Flow", "Logs"]);
  });

  it('should have two tabs with a context', () => {
    expect(testTabs(component, fixture, {
      "ctx": { "foo": "bar" }
    }))
      .toEqual(["Flow", "Context"]);
  });

  it('should have two tabs with a residue', () => {
    expect(testTabs(component, fixture, {
      "rsd": [{ "foo": "bar" }]
    }))
      .toEqual(["Flow", "Residue"]);
  });

  it('should have all tabs', () => {
    expect(testTabs(component, fixture, {
      "logs": [{}],
      "ctx": { "foo": "bar" },
      "rsd": [{ "foo": "bar" }]
    }))
      .toEqual(["Flow", "Context", "Residue", "Logs"]);
  });
});

function testTabs(component: DetailComponent, fixture: ComponentFixture<DetailComponent>, args: any): string[] {
  if (args.logs != undefined) {
    component.flow.logs = args.logs;
  }
  if (args.ctx != undefined) {
    component.flow.context = args.ctx;
  }
  if (args.rsd != undefined) {
    component.flow.residue = args.rsd;
  }

  fixture.detectChanges();

  let nl: NodeList = fixture.nativeElement
    .querySelectorAll("div.mat-tab-label-content");
  return Array.from(nl).map(e => e.textContent?.trim() || "???");
}

@Component({
  selector: 'app-log-view',
  template: ''
})
class StubLogView {
  @Input() logs: LogEvent[] = [];
}

@Component({
  selector: 'app-flow-sequence',
  template: ''
})
class StubFlowSequence {
  @Input() sequence: SequenceData = { entity: [], item: [] };
}

@Component({
  selector: 'app-view-options',
  template: ''
})
class StubViewOptions {
  @Input() options: Options = new Options();
}

@Component({
  selector: 'app-transmission',
  template: ''
})
class StubTransmission {
  @Input() options: Options = new Options();
  @Input() display: Display = Display.Actual;
  @Input() diffType: DiffType = DiffType.Asserted;
  @Input() diffFormat: DiffDisplay = 'unified';
}

@Component({
  selector: 'markdown',
  template: ''
})
class StubMarkdown {
}