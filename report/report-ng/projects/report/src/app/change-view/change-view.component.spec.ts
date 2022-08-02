import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChangeViewComponent } from './change-view.component';

describe('ChangeViewComponent', () => {
  let component: ChangeViewComponent;
  let fixture: ComponentFixture<ChangeViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ChangeViewComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ChangeViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
