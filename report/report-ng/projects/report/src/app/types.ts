/**
 * The tag values that are used to denote assertion result, and so
 * should not be considered part of the flow's identity
 */
const resultTags: Set<string> = new Set(["PASS", "FAIL", "SKIP", "ERROR"]);
/**
 * 
 * @param tag a tag value
 * @returns true if that value is a result tag
 */
export function isResultTag(tag: string) {
  return resultTags.has(tag);
}

/**
 * Remove result tags form the supplied sets
 * @param tagSets Some sets of tags
 */
export function removeResultTagsFrom(...tagSets: Set<string>[]) {
  tagSets.forEach(tagSet =>
    resultTags.forEach(t => tagSet.delete(t)));
}

/**
 * Mirrors com.mastercard.test.flow.report.data.Index
 */
export interface Index {
  meta: Meta;
  entries: Entry[];
}

/**
 * Type guard to turn arbitrary data into our Index type
 * @param data  A data sturcture
 * @returns true if the data has an array of entry structures
 */
export function isIndex(data: any): data is Index {
  return data
    && data.meta
    && isMeta(data.meta)
    && data.entries
    && Array.isArray(data.entries)
    && data.entries.every(isEntry);
}

export interface Meta {
  modelTitle: string;
  testTitle: string;
  timestamp: number;
}

/**
 * Type guard to turn arbitary data into our Meta type
 * @param data A data structure
 * @returns  true if the data has title and timestamp members
 */
function isMeta(data: any): data is Meta {
  return data
    && data.modelTitle != null
    && typeof data.modelTitle === 'string'
    && data.testTitle != null
    && typeof data.testTitle === 'string'
    && data.timestamp != null
    && typeof data.timestamp === 'number';
}

/**
 * Mirrors com.mastercard.test.flow.report.data.Entry
 */
export interface Entry {
  description: string;
  tags: string[];
  detail: string;
}

/**
 * Type guard to turn arbitrary data into our Entry type
 * @param data A data structure
 * @returns true if the data has description, tags and detail members
 */
function isEntry(data: any): data is Entry {
  return data
    && data.description != null
    && typeof (data.description) === 'string'
    && Array.isArray(data.tags)
    && data.tags.every((item: any) => typeof item === 'string')
    && data.detail
    && typeof (data.detail) === 'string';
}

export const empty_index: Index = {
  meta: { modelTitle: "", testTitle: "", timestamp: 0 },
  entries: []
}

import { DiffDisplay } from "./text-diff/text-diff.component";

export class Options {
  display: Display = Display.Actual;
  dataDisplay: DataDisplay = DataDisplay.Human;
  diffType: DiffType = DiffType.Asserted;
  diffFormat: DiffDisplay = 'unified';
}

export enum Display {
  Expected = "Expected",
  Diff = "Diff",
  Actual = "Actual",
  Basis = "Basis"
}

export enum DiffType {
  Full = "Full",
  Asserted = "Asserted",
}

export enum DataDisplay {
  Human = "Human",
  /**
   * I'd really have quite liked to call this "UTF8", but numbers 
   * in enum names appears to breaks comparison? This issue is
   * surprisingly hard to google!
   */
  UTF = "UTF",
  Hex = "Hex"
}

export function isDiffFormat(data: any): data is DiffDisplay {
  return typeof data === 'string'
    && (data === 'unified' || data === 'split');
}

/**
 * Mirrors com.mastercard.test.flow.report.data.FlowData
 */
export interface Flow {
  description: string;
  tags: string[];
  motivation: string;
  trace: string;
  basis?: string;
  context: any;
  residue: Residue[];
  dependencies: Dependencies;
  root: Interaction;
  logs: LogEvent[];
}

/**
 * Type guard to turn arbitrary data into our Flow type
 * @param data A data structure
 * @returns true if the data matches the Flow structure
 */
export function isFlow(data: any): data is Flow {
  return data
    && data.description != null
    && typeof (data.description) === 'string'
    && Array.isArray(data.tags)
    && data.tags.every((item: any) => typeof item === 'string')
    && data.motivation != null
    && typeof (data.motivation) === 'string'
    && data.trace != null
    && typeof (data.trace) === 'string'
    && (data.basis == null || typeof (data.basis) === 'string')
    && isDependencies(data.dependencies)
    && data.root
    && isInteraction(data.root)
    && Array.isArray(data.residue)
    && data.residue.every(isResidue)
    && Array.isArray(data.logs)
    && data.logs.every(isLogEvent);
  ;
}

/**
 * Checks for the presence of masked message data in the interactions
 * @param flow 
 * @returns true if any message assertions are possible
 */
export function flowsAsserted(flow: Flow): boolean {
  return interactionAsserted(flow.root);
}

/**
 * Checks if message assertions are successful
 * @param flow 
 * @returns true if all masked expected and actual messages are equal
 */
export function flowsAssertionsPassed(flow: Flow): boolean {
  return interactionAssertionPassed(flow.root);
}

export function residuesAsserted(flow: Flow): boolean {
  return flow.residue.find(r => residueAsserted(r)) !== undefined;
}

export function residueAssertionsPassed(flow: Flow): boolean {
  return flow.residue.every(r => residueAssertionPassed(r));
}

export interface Dependencies {
  [detail: string]: Dependency;
}

function isDependencies(data: any): data is Dependencies {
  return data
    && Object.keys(data)
      .every(k => typeof (k) === 'string'
        && isDependency(data[k]));
}

/**
 * Mirrors com.mastercard.test.flow.report.data.DependencyData
 */
export interface Dependency {
  description: string;
  tags: string[];
}
/**
 * Type guard to turn arbitrary data into our Dependency type
 * @param data A data structure
 * @returns true if the data matches the Dependency structure
 */
function isDependency(data: any): data is Dependency {
  return data
    && data.description
    && typeof (data.description) === 'string'
    && Array.isArray(data.tags)
    && data.tags.every((item: any) => typeof item === 'string');
}

/**
 * Mirrors com.mastercard.test.flow.report.data.InteractionData
 */
export interface Interaction {
  requester: string;
  request: Transmission;
  responder: string;
  response: Transmission;
  tags: string[];
  children: Interaction[];
}

/**
 * Type guard to turn arbitrary data into our Interaction type
 * @param data A data structure
 * @returns true if the data matches the Interaction structure
 */
function isInteraction(data: any): data is Interaction {
  return data
    && data.requester != null
    && typeof (data.requester) === 'string'
    && data.responder != null
    && typeof (data.responder) === 'string'
    && Array.isArray(data.tags)
    && data.tags.every((item: any) => typeof item === 'string')
    && data.request
    && isTransmission(data.request)
    && data.response
    && isTransmission(data.response)
    && data.children
    && data.children.every(isInteraction);
}

/**
 * Searches for message assertions in the interaction tree
 * @param ntr 
 * @returns true if message assertion exist in this or any child intertaction
 */
function interactionAsserted(ntr: Interaction): boolean {
  return transmissionAsserted(ntr.request)
    || transmissionAsserted(ntr.response)
    || ntr.children.find(c => interactionAsserted(c)) !== undefined;
}

/**
 * Checks the success of message assertions in the interaction tree
 * @param ntr 
 * @returns true is all message assertions in this and all child interactions are successful
 */
function interactionAssertionPassed(ntr: Interaction): boolean {
  return (!transmissionAsserted(ntr.request) || transmissionAssertionPassed(ntr.request))
    && (!transmissionAsserted(ntr.response) || transmissionAssertionPassed(ntr.response))
    && ntr.children
      .every(c => interactionAssertionPassed(c));
}

/**
 * Mirrors com.mastercard.test.flow.report.data.TransmissionData
 */
export interface Transmission {
  full: Message;
  asserted?: Asserted;
}

/**
 * Type guard to turn arbitrary data into our Transmission type
 * @param data A data structure
 * @returns true if the data matches the Transmission structure
 */
export function isTransmission(data: any): data is Transmission {
  return data.full
    && isMessage(data.full)
    && (data.asserted == null || isAsserted(data.asserted));
}

/**
 * Checks for message assertion
 * @param tx 
 * @returns true is masked data is available for comparison
 */
function transmissionAsserted(tx: Transmission): boolean {
  return tx.asserted !== undefined
    && tx.asserted.actual !== null
    && tx.asserted.expect !== null;
}

/**
 * Checks for message assertion success
 * @param tx 
 * @returns true is masked data is available for assertion and the actual message is the same as the expected
 */
function transmissionAssertionPassed(tx: Transmission): boolean {
  return transmissionAsserted(tx)
    && tx.asserted!.actual === tx.asserted!.expect
}

/**
 * Mirrors com.mastercard.test.flow.report.data.MessageData
 */
export interface Message {
  expect: string;
  expectBytes: string;
  actual?: string;
  actualBytes?: string;
}

/**
 * Type guard to turn arbitrary data into our Message type
 * @param data A data structure
 * @returns true if the data matches the Message structure
 */
function isMessage(data: any): data is Message {
  return data
    && data.expect != null
    && typeof (data.expect) === 'string'
    && data.expectBytes != null
    && typeof (data.expectBytes) === 'string'
    && (data.actual == null || typeof (data.actual) === 'string')
    && (data.actualBytes == null || typeof (data.actualBytes) === 'string');
}

/**
 * Mirrors com.mastercard.test.flow.report.data.TransmissionData
 */
export interface Asserted {
  expect?: string;
  actual?: string;
}

/**
 * Type guard to turn arbitrary data into our Asserted type
 * @param data A data structure
 * @returns true if the data matches the Asserted structure
 */
function isAsserted(data: any): data is Asserted {
  return data
    && (data.actual == null || typeof (data.actual) === 'string')
    && (data.expect == null || typeof (data.expect) === 'string');
}

/**
 * Mirrors com.mastercard.test.flow.report.data.LogEvent
 */
export interface LogEvent {
  time: string;
  level: string;
  source: string;
  message: string;
}

/**
 * Type guard to turn arbitrary data into our LogEvent type
 * @param data A data structure
 * @returns true if the data matches the LogEvent structure
 */
function isLogEvent(data: any): data is LogEvent {
  return data
    && data.time != null
    && typeof (data.time) === 'string'
    && data.level != null
    && typeof (data.level) === 'string'
    && data.source != null
    && typeof (data.source) === 'string'
    && data.message != null
    && typeof (data.message) === 'string';
}

/**
 * Mirrors com.mastercard.test.flow.report.data.LR
 */
export interface Residue {
  name: string;
  raw: any;
  full: Asserted;
  masked: Asserted;
}

/**
 * Type guard to turn arbitrary data into our Residue type
 * @param data A data structure
 * @returns true if the data matches the Residue structure
 */
function isResidue(data: any): data is Residue {
  return data
    && data.name && typeof (data.name) === 'string'
    && data.raw
    && (data.full === null || isAsserted(data.full))
    && (data.masked === null || isAsserted(data.masked));
}

export function residueAsserted(residue: Residue): boolean {
  return residue.masked !== null
    && (residue.masked.actual !== null
      || residue.masked.expect !== null);
}

export function residueAssertionPassed(residue: Residue): boolean {
  return (residue.masked.actual ?? '') === (residue.masked.expect ?? '');
}

export const empty_message: Message = {
  expect: "",
  expectBytes: "",
  actual: "",
  actualBytes: "",
}

export const empty_asserted: Asserted = {
  expect: "",
  actual: "",
}

export const empty_transmission: Transmission = {
  full: empty_message,
  asserted: empty_asserted
};

export const empty_interaction: Interaction = {
  requester: "",
  request: empty_transmission,
  responder: "",
  response: empty_transmission,
  tags: [],
  children: []
};

export const empty_flow: Flow = {
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