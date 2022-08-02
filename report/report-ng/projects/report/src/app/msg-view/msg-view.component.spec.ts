import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MsgViewComponent } from './msg-view.component';

describe('MsgViewComponent', () => {
  let component: MsgViewComponent;
  let fixture: ComponentFixture<MsgViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ MsgViewComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(MsgViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
