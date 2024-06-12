import { TestBed } from '@angular/core/testing';

import { BasisFetchService, setDistance } from './basis-fetch.service';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Flow, Interaction, empty_flow, empty_interaction, empty_transmission } from './types';
import { defer, of } from 'rxjs';
import { Action, empty_action } from './seq-action/seq-action.component';

describe('BasisFetchService', () => {
  let httpClientSpy: jasmine.SpyObj<HttpClient>;
  let service: BasisFetchService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    httpClientSpy = jasmine.createSpyObj('HttpClient', ['get']);
    service = new BasisFetchService(httpClientSpy);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should request a flow', () => {
    httpClientSpy.get.and.returnValue(detailPage(empty_flow));

    service.get("detail_hash");

    expect(httpClientSpy.get.calls.count())
      .withContext('one call')
      .toBe(1);
    expect(httpClientSpy.get.calls.mostRecent().args[0])
      .withContext("request path")
      .toBe("detail_hash.html");
  });

  it('should cope with bad data', () => {
    httpClientSpy.get.and.returnValue(of("not a detail page"));
    service.get("detail_hash");

    expect(service.message(action("", "", "")))
      .withContext("nothing there")
      .toBe(null);
  });

  it('should cope with malformed json', () => {
    httpClientSpy.get.and.returnValue(of("// START_JSON_DATA\n{]\n// END_JSON_DATA"));
    service.get("detail_hash");

    expect(service.message(action("", "", "")))
      .withContext("nothing there")
      .toBe(null);
  });

  it('should cope with missing data', () => {
    httpClientSpy.get.and.returnValue(of("// START_JSON_DATA\n{}\n// END_JSON_DATA"));
    service.get("detail_hash");

    expect(service.message(action("", "", "")))
      .withContext("nothing there")
      .toBe(null);
  });

  it('should match simple interactions', () => {
    let flow = flowWithRoot(interaction("AVA", "BEN"));
    httpClientSpy.get.and.returnValue(detailPage(flow));

    service.get("detail_hash");

    expect(service.message(action("AVA", "BEN", "BEN request")))
      .withContext("matched request")
      .toBe("request from AVA to BEN with tags []");

    expect(service.message(action("BEN", "AVA", "BEN response")))
      .withContext("matched response")
      .toBe("response from BEN to AVA with tags []");

    expect(service.message(action("AVA", "BEN", "BEN foobar")))
      .withContext("bad label")
      .toBe(null);

    expect(service.message(action("AVA", "CHE", "CHE request")))
      .withContext("actor mismatch")
      .toBe(null);
  });

  it('should cope with tagging', () => {
    let flow = flowWithRoot(interaction("AVA", "BEN", "abc"));
    httpClientSpy.get.and.returnValue(detailPage(flow));

    service.get("detail_hash");

    expect(service.message(action("AVA", "BEN", "BEN request", "abc")))
      .withContext("tag match")
      .toBe("request from AVA to BEN with tags [abc]");

    expect(service.message(action("AVA", "BEN", "BEN request")))
      .withContext("no tags on query")
      .toBe("request from AVA to BEN with tags [abc]");

    expect(service.message(action("AVA", "BEN", "BEN request", "def")))
      .withContext("mismatched tags on query")
      .toBe("request from AVA to BEN with tags [abc]");
  });

  it('should find the best match', () => {
    let flow = flowWithRoot(interaction("AVA", "BEN"));
    flow.root.children = [
      interaction("BEN", "CHE", "abc"),
      interaction("BEN", "CHE", "abc", "def"),
      interaction("BEN", "CHE", "def"),
    ];
    httpClientSpy.get.and.returnValue(detailPage(flow));

    service.get("detail_hash");

    expect(service.message(action("BEN", "CHE", "CHE request", "abc")))
      .withContext("first")
      .toBe("request from BEN to CHE with tags [abc]");

    expect(service.message(action("BEN", "CHE", "CHE request", "def")))
      .withContext("third")
      .toBe("request from BEN to CHE with tags [def]");

    expect(service.message(action("BEN", "CHE", "CHE request", "abc", "def")))
      .withContext("second")
      .toBe("request from BEN to CHE with tags [abc,def]");
  });

  it('should compute set distance correctly', () => {
    expect(service).toBeTruthy();
    expect(setDistance([], []))
      .withContext("empty")
      .toBe(0);

    expect(setDistance(["a"], []))
      .withContext("single removed")
      .toBe(1);
    expect(setDistance([], ["a"]))
      .withContext("single added")
      .toBe(1);

    expect(setDistance(["a", "b", "c"], []))
      .withContext("multi removed")
      .toBe(1);
    expect(setDistance([], ["a", "b", "c"]))
      .withContext("multi added")
      .toBe(1);

    expect(setDistance(["a"], ["a"]))
      .withContext("single match")
      .toBe(0);
    expect(setDistance(["a"], ["b"]))
      .withContext("single mismatch")
      .toBe(1);

    expect(setDistance(["a", "b", "c"], ["a", "b", "c"]))
      .withContext("full match")
      .toBe(0);
    expect(setDistance(["a", "b", "c"], ["a", "b", "c", "d"]))
      .withContext("better match")
      .toBe(0.25);
    expect(setDistance(["a"], ["a", "b", "c", "d"]))
      .withContext("slight match")
      .toBe(0.75);
    expect(setDistance(["a", "w", "x"], ["a", "y", "z"]))
      .withContext("worse match")
      .toBe(0.8);
    expect(setDistance(["a", "b", "c"], ["d", "e", "f"]))
      .withContext("disjoint")
      .toBe(1);
  });

});

function detailPage(flow: Flow) {
  let lines = [
    "blah blah blah",
    "// START_JSON_DATA",
  ];
  JSON.stringify(flow, null, 2).split("\n").forEach(l => lines.push(l));
  lines.push(
    "// END_JSON_DATA",
    "blah blah blah",
  );
  let page = lines.map((l) => l + "\n").join("");
  return of(page);
}

function flowWithRoot(interaction: Interaction): Flow {
  let flow: Flow = JSON.parse(JSON.stringify(empty_flow));
  flow.root = interaction;
  return flow;
}

function interaction(from: string, to: string, ...tags: string[]) {
  let interaction = JSON.parse(JSON.stringify(empty_interaction));
  interaction.requester = from;
  interaction.request.full.expect = `request from ${from} to ${to} with tags [${tags}]`;
  interaction.responder = to;
  interaction.response.full.expect = `response from ${to} to ${from} with tags [${tags}]`;
  interaction.tags = tags;
  return interaction;
}

function action(from: string, to: string, label: string, ...tags: string[]): Action {
  let action: Action = JSON.parse(JSON.stringify(empty_action));
  action.fromName = from;
  action.toName = to;
  action.label = label;
  action.tags = tags;
  return action;
}