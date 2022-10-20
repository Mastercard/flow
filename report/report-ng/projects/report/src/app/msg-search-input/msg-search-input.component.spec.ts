import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MsgSearchInputComponent } from './msg-search-input.component';

describe('MsgSearchInputComponent', () => {
  let component: MsgSearchInputComponent;
  let fixture: ComponentFixture<MsgSearchInputComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ MsgSearchInputComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MsgSearchInputComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
