import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, Input, provideZoneChangeDetection, ChangeDetectionStrategy } from '@angular/core';
import { ModelDiffComponent } from './model-diff.component';
import { RouterTestingModule } from '@angular/router/testing';
import { FlowPairingService } from '../flow-pairing.service';
import { ModelDiffDataService } from '../model-diff-data.service';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ModelDiffDataSourceComponent } from '../model-diff-data-source/model-diff-data-source.component';
import { ChangeViewComponent } from '../change-view/change-view.component';
import { ChangeAnalysisComponent } from '../change-analysis/change-analysis.component';

describe('ModelDiffComponent', () => {
  let component: ModelDiffComponent;
  let fixture: ComponentFixture<ModelDiffComponent>;
  let mockFps;
  let mockMdds;

  beforeEach(async () => {
    mockFps = jasmine.createSpyObj([
      'onUnpair', 'onPair', 'onRebuild', 'buildQuery',
      'paired', 'unpairedLeftEntries', 'unpairedRightEntries']);
    mockFps.paired.and.returnValue([]);
    mockFps.unpairedLeftEntries.and.returnValue([]);
    mockFps.unpairedRightEntries.and.returnValue([]);

    mockMdds = jasmine.createSpyObj(['onFlow', 'onIndex', 'index']);

    await TestBed.configureTestingModule({
      declarations: [
        ModelDiffComponent,

        StubMenu,
        StubFlowFilter,
        StubModelDiffDataSource,
        StubPairedFlowList,
        StubUnpairedFlowList,
        StubChangeView,
        StubChangeAnalysis,
      ],
      providers: [
        provideZoneChangeDetection(),
        { provide: FlowPairingService, useValue: mockFps },
        { provide: ModelDiffDataService, useValue: mockMdds },
      ],
      imports: [
        RouterTestingModule,
        MatExpansionModule,
        MatToolbarModule,
        MatProgressBarModule,
        MatTabsModule,
        MatIconModule,
        BrowserAnimationsModule,
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ModelDiffComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});


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
}

@Component({
    selector: 'app-model-diff-data-source',
    template: '',
    providers: [{
            provide: ModelDiffDataSourceComponent,
            useClass: StubModelDiffDataSource
        }],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubModelDiffDataSource {

  path(label: string, value: string): void {
  }

  getValue(): string {
    return "";
  }

  valueChanges(observer: (value: any) => void): void {
  }
}

@Component({
    selector: 'app-paired-flow-list',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubPairedFlowList {
}

@Component({
    selector: 'app-unpaired-flow-list',
    template: '',
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubUnpairedFlowList {
}

@Component({
    selector: 'app-change-view',
    template: '',
    providers: [{
            provide: ChangeViewComponent,
            useClass: StubChangeView,
        }],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubChangeView {
  onSelection(cb: () => void): void {
  }
}

@Component({
    selector: 'app-change-analysis',
    template: '',
    providers: [{
            provide: ChangeAnalysisComponent,
            useClass: StubChangeAnalysis,
        }],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
class StubChangeAnalysis {
  toChangeView(t: (from: string, to: string) => void): void {
  }
}