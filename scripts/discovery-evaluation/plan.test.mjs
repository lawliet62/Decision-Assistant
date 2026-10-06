import { test } from "node:test";
import assert from "node:assert/strict";
import { selectRemaining } from "./plan.mjs";

const cases = [{id:"one"},{id:"two"},{id:"three"}];
const identity = {variant:"a",commit:"abc",jarSha256:"jar",casesSha256:"cases"};
const previous = {...identity,completed:1,unexecuted:["two","three"]};
const rows = [{caseId:"one",decisionId:"decision",metrics:{coverageComplete:true},discovery:{status:"PARTIAL"}}];
const created = [{caseId:"one",decisionId:"decision"}];

test("실패 결과도 재실행하지 않고 시작하지 않은 조건만 선택한다", () => {
  assert.deepEqual(selectRemaining(cases,previous,rows,created,identity), cases.slice(1));
});
test("다른 바이너리나 입력과 결과를 혼합하지 않는다", () => {
  for (const key of Object.keys(identity)) assert.throws(() => selectRemaining(cases,previous,rows,created,{...identity,[key]:"other"}));
});

test("보완 문서 동의와 호출 상한이 달라진 실행은 이어 붙이지 않는다", () => {
  assert.throws(() => selectRemaining(cases, previous, rows, created, {...identity, officialEvidenceConsent:true}));
  assert.deepEqual(selectRemaining(cases, previous, rows, created, {...identity, officialEvidenceConsent:false}), cases.slice(1));
  assert.deepEqual(selectRemaining(cases, {...previous, officialEvidenceConsent:true}, rows, created,
    {...identity, officialEvidenceConsent:true}), cases.slice(1));
});
test("실행 중이거나 계측이 누락된 작업은 자동으로 다시 만들지 않는다", () => {
  assert.throws(() => selectRemaining(cases,previous,[],created,identity));
  assert.throws(() => selectRemaining(cases,previous,[{...rows[0],metrics:{coverageComplete:false}}],created,identity));
  assert.throws(() => selectRemaining(cases,{...previous,unexecuted:["three"]},rows,created,identity));
});
