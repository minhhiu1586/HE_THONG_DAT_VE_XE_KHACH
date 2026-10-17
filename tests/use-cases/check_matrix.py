"""Validate T06 coverage and recorded results; does not execute business APIs."""
import json
from collections import Counter
from pathlib import Path


def main():
    data = json.loads(Path(__file__).with_name('test-cases.json').read_text(encoding='utf-8'))
    registry = {uc['id']: uc for uc in data['use_cases']}
    assert set(registry) == {f'UC-{i:02}' for i in range(1, 24) if i!=21}, 'UC-21 was removed with ticket inspection'
    cases = data['test_cases']
    ids = [case['test_case_id'] for case in cases]
    assert len(ids) == len(set(ids)), 'Duplicate test case ID'
    required = {'test_case_id', 'use_case_id', 'use_case_name', 'actor', 'group',
                'objective', 'preconditions', 'input', 'steps', 'expected_result',
                'actual_result', 'status', 'executor', 'owner', 'evidence', 'notes', 'source'}
    for case in cases:
        assert required <= case.keys(), f'Missing fields: {case.get("test_case_id")}'
        assert case['use_case_id'] in registry, case['test_case_id']
        assert case['use_case_name'] == registry[case['use_case_id']]['name'], case['test_case_id']
        assert case['group'] in {'Positive', 'Negative', 'Boundary'}, case['test_case_id']
        assert case['status'] in {'Pass', 'Fail', 'Not Run', 'Blocked'}, case['test_case_id']
        assert case['steps'] and case['expected_result'] and case['owner'], case['test_case_id']
        if case['status'] in {'Pass', 'Fail'}:
            assert case['actual_result'] and case['executor'] and case['evidence'], case['test_case_id']
            for evidence in case['evidence']:
                if not evidence.startswith(('https://', 'http://')):
                    assert (Path(__file__).resolve().parents[2] / evidence).exists(), evidence
        if case['status'] == 'Blocked':
            assert case['notes'].strip(), f'Missing blocker: {case["test_case_id"]}'
    for uc in registry:
        groups = {case['group'] for case in cases if case['use_case_id'] == uc}
        assert groups == {'Positive', 'Negative', 'Boundary'}, f'Incomplete coverage: {uc}'
    print(f'PASS: {len(registry)} use cases, {len(cases)} unique cases, all three groups covered.')
    print(dict(Counter(case['status'] for case in cases)))


if __name__ == '__main__':
    main()
