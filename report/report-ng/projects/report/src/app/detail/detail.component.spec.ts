import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input, provideZoneChangeDetection, ChangeDetectionStrategy } from '@angular/core';
import { DetailComponent } from './detail.component';
import { BasisFetchService } from '../basis-fetch.service';
import { MatMenuModule } from '@angular/material/menu';
import { MatListModule } from '@angular/material/list';
import { DiffType, Display, LogEvent, Options, Residue, empty_flow, empty_interaction } from '../types';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { SequenceData } from '../flow-sequence/flow-sequence.component';
import { DiffDisplay } from '../text-diff/text-diff.component';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatTabGroupHarness } from '@angular/material/tabs/testing';
import { By } from '@angular/platform-browser';
import { MatTabGroup } from '@angular/material/tabs';

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
        StubContextView,
        StubResidueView,
        StubFlowSequence,
        StubTransmission,
        StubMarkdown,
        StubViewOptions,
      ],
      providers: [
        { provide: BasisFetchService, useValue: mockBasisFetch },
        provideZoneChangeDetection(),
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
      exercised: [],
      logs: [],
    };
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have one tab with no logs, context or residue', async () => {
    expect(await testTabs(component, fixture, {
    }))
      .toEqual(["Flow"]);
  });

  it('should have two tabs with logs', async () => {
    expect(await testTabs(component, fixture, {
      "logs": [{}]
    }))
      .toEqual(["Flow", "Logs"]);
  });

  it('should have two tabs with a context', async () => {
    expect(await testTabs(component, fixture, {
      "ctx": { "foo": "bar" }
    }))
      .toEqual(["Flow", "Context"]);
  });

  it('should have two tabs with a residue', async () => {
    expect(await testTabs(component, fixture, {
      "rsd": [{ "foo": "bar" }]
    }))
      .toEqual(["Flow", "Residue"]);
  });

  it('should have all tabs', async () => {
    expect(await testTabs(component, fixture, {
      "logs": [{}],
      "ctx": { "foo": "bar" },
      "rsd": [{ "foo": "bar" }]
    }))
      .toEqual(["Flow", "Context", "Residue", "Logs"]);
  });

  it('keeps detail tabs at their natural width', () => {
    const tabGroup = fixture.debugElement.query(By.directive(MatTabGroup));
    expect((tabGroup.componentInstance as MatTabGroup).stretchTabs).toBeFalse();
  });

  it('separates navigation, flow summary and detail tabs', () => {
    const navigation: HTMLElement = fixture.nativeElement.querySelector('.list_horizontal');
    const summary: HTMLElement = fixture.nativeElement.querySelector('.flow-summary');
    const tabs: HTMLElement = fixture.nativeElement.querySelector('.detail-tabs');
    const navigationItems = Array.from(navigation.children, item => item as HTMLElement);

    expect(getComputedStyle(navigation).borderBottomStyle).toBe('solid');
    navigationItems.forEach(item => expect(getComputedStyle(item).borderRightStyle).toBe('solid'));
    expect(getComputedStyle(summary).backgroundColor).not.toBe('rgba(0, 0, 0, 0)');
    expect(getComputedStyle(summary).borderBottomStyle).toBe('solid');
    expect(getComputedStyle(tabs).borderTopStyle).toBe('solid');
  });
});

async function testTabs(component: DetailComponent, fixture: ComponentFixture<DetailComponent>, args: any): Promise<string[]> {
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

  const loader = TestbedHarnessEnvironment.loader(fixture);
  const tabGroup = await loader.getHarness(MatTabGroupHarness);
  const tabs = await tabGroup.getTabs();
  return Promise.all(tabs.map(tab => tab.getLabel()));
}

@Component({
    selector: 'app-log-view',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubLogView {
  @Input() logs: LogEvent[] = [];
}

@Component({
    selector: 'app-context-view',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubContextView {
  @Input() context: any = {};
}

@Component({
    selector: 'app-residue-view',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubResidueView {
  @Input() residues: Residue[] = [];
}

@Component({
    selector: 'app-flow-sequence',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubFlowSequence {
  @Input() sequence: SequenceData = { entity: [], exercised: [], item: [] };
}

@Component({
    selector: 'app-view-options',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubViewOptions {
  @Input() options: Options = new Options();
}

@Component({
    selector: 'app-transmission',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubTransmission {
  @Input() options: Options = new Options();
  @Input() display: Display = Display.Actual;
  @Input() diffType: DiffType = DiffType.Asserted;
  @Input() diffFormat: DiffDisplay = 'unified';
}

@Component({
    selector: 'markdown',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubMarkdown {
}