import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewOptionsComponent } from './view-options.component';
import { EnumIteratePipe } from '../enum-iterate.pipe';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { MatButtonToggleGroup } from '@angular/material/button-toggle';
import { Display } from '../types';

describe('ViewOptionsComponent', () => {
  let component: ViewOptionsComponent;
  let fixture: ComponentFixture<ViewOptionsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [
        ViewOptionsComponent,
        EnumIteratePipe,
      ],
      imports: [
        FormsModule,
        MatIconModule,
        MatButtonToggleModule,
        MatTooltipModule,
      ],
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ViewOptionsComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('hides indicators on display controls', () => {
    fixture.detectChanges();

    fixture.debugElement
      .queryAll(By.directive(MatButtonToggleGroup))
      .forEach(group => expect(
        group.injector.get(MatButtonToggleGroup).hideSingleSelectionIndicator
      ).toBeTrue());
  });

  it('hides indicators on diff controls', () => {
    component.options.display = Display.Diff;
    fixture.detectChanges();

    fixture.debugElement
      .queryAll(By.directive(MatButtonToggleGroup))
      .forEach(group => expect(
        group.injector.get(MatButtonToggleGroup).hideSingleSelectionIndicator
      ).toBeTrue());
  });
});
