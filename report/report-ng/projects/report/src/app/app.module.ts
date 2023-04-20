import { NgModule } from '@angular/core';
import { BrowserModule, Title } from '@angular/platform-browser';

import { AppComponent } from './app.component';
import { IndexComponent } from './index/index.component';
import { IndexRouteComponent } from './index-route/index-route.component';
import { DetailComponent } from './detail/detail.component';
import { ContextViewComponent } from './context-view/context-view.component';
import { EnumIteratePipe } from './enum-iterate.pipe';
import { FlowSequenceComponent } from './flow-sequence/flow-sequence.component';
import { HexdumpComponent } from './hexdump/hexdump.component';
import { LogViewComponent } from './log-view/log-view.component';
import { MsgViewComponent } from './msg-view/msg-view.component';
import { SeqActionComponent } from './seq-action/seq-action.component';
import { SeqNoteComponent } from './seq-note/seq-note.component';
import { SeqSectionComponent } from './seq-section/seq-section.component';
import { TransmissionComponent } from './transmission/transmission.component';
import { ViewOptionsComponent } from './view-options/view-options.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { MarkdownModule } from 'ngx-markdown';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatLegacyFormFieldModule as MatFormFieldModule } from '@angular/material/legacy-form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatLegacyInputModule as MatInputModule } from '@angular/material/legacy-input';
import { MatLegacyListModule as MatListModule } from '@angular/material/legacy-list';
import { MatLegacyMenuModule as MatMenuModule } from '@angular/material/legacy-menu';
import { MatLegacySelectModule as MatSelectModule } from '@angular/material/legacy-select';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatLegacyTableModule as MatTableModule } from '@angular/material/legacy-table';
import { MatLegacyTabsModule as MatTabsModule } from '@angular/material/legacy-tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatLegacyTooltipModule as MatTooltipModule } from '@angular/material/legacy-tooltip';
import { NgxTextDiffModule } from 'ngx-text-diff';
import { ChangeViewComponent } from './change-view/change-view.component';
import { FlowFilterComponent } from './flow-filter/flow-filter.component';
import { FlowNavItemComponent } from './flow-nav-item/flow-nav-item.component';
import { FlowNavListComponent } from './flow-nav-list/flow-nav-list.component';
import { MenuComponent } from './menu/menu.component';
import { ModelDiffComponent } from './model-diff/model-diff.component';
import { ModelDiffDataSourceComponent } from './model-diff-data-source/model-diff-data-source.component';
import { PairedFlowListComponent } from './paired-flow-list/paired-flow-list.component';
import { PairSelectItemComponent } from './pair-select-item/pair-select-item.component';
import { TagComponent } from './tag/tag.component';
import { TagFilterComponent } from './tag-filter/tag-filter.component';
import { UnpairedFlowListComponent } from './unpaired-flow-list/unpaired-flow-list.component';
import { ChangeAnalysisComponent } from './change-analysis/change-analysis.component';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { MatLegacyAutocompleteModule as MatAutocompleteModule } from '@angular/material/legacy-autocomplete';
import { MatLegacyChipsModule as MatChipsModule } from '@angular/material/legacy-chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatLegacyProgressBarModule as MatProgressBarModule } from '@angular/material/legacy-progress-bar';
import { RouterModule, Routes } from '@angular/router';
import { TagSummaryComponent } from './tag-summary/tag-summary.component';
import { ResidueViewComponent } from './residue-view/residue-view.component';
import { MsgSearchInputComponent } from './msg-search-input/msg-search-input.component';
import { MatRipple, MatRippleModule } from '@angular/material/core';
import { HighlightedTextComponent } from './highlighted-text/highlighted-text.component';

const routes: Routes = [
  { path: "diff", component: ModelDiffComponent },
  { path: "**", component: IndexComponent },
]

@NgModule({
  declarations: [
    AppComponent,
    IndexComponent,
    IndexRouteComponent,
    DetailComponent,
    ContextViewComponent,
    EnumIteratePipe,
    FlowSequenceComponent,
    HexdumpComponent,
    LogViewComponent,
    MsgViewComponent,
    SeqActionComponent,
    SeqNoteComponent,
    SeqSectionComponent,
    TransmissionComponent,
    ViewOptionsComponent,
    ChangeViewComponent,
    FlowFilterComponent,
    FlowNavItemComponent,
    FlowNavListComponent,
    IndexComponent,
    MenuComponent,
    ModelDiffComponent,
    ModelDiffDataSourceComponent,
    PairedFlowListComponent,
    PairSelectItemComponent,
    TagComponent,
    TagFilterComponent,
    UnpairedFlowListComponent,
    ChangeAnalysisComponent,
    TagSummaryComponent,
    ResidueViewComponent,
    MsgSearchInputComponent,
    HighlightedTextComponent,
  ],
  imports: [
    BrowserAnimationsModule,
    BrowserModule,
    DragDropModule,
    FormsModule,
    HttpClientModule,
    MarkdownModule.forRoot(),
    MatAutocompleteModule,
    MatButtonToggleModule,
    MatChipsModule,
    MatDividerModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatListModule,
    MatMenuModule,
    MatProgressBarModule,
    MatRippleModule,
    MatSelectModule,
    MatSidenavModule,
    MatTableModule,
    MatTabsModule,
    MatToolbarModule,
    MatTooltipModule,
    NgxTextDiffModule,
    ReactiveFormsModule,
    RouterModule.forRoot(routes, { useHash: true }),
  ],
  providers: [Title],
  bootstrap: [AppComponent]
})
export class AppModule { }
