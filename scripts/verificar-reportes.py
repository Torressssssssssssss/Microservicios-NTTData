#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
reports=list((root/'tacocloud').glob('*/target/*-reports/TEST-*.xml'))
required={'FunctionalChallengesTest','BusinessRulesTest','EmailAndContractTest','SecurityAndRegistrationTest',
          'TransportTest','CorrelationAndIdempotencyTest','OpenApiContractTest','RabbitRetryTest',
          'IngredientMongoIT','TransactionalChallengesIT','RuntimeMvcIT'}
seen=set();total=0
for report in reports:
    suite=ET.parse(report).getroot()
    seen.add(suite.get('name','').split('.')[-1])
    total+=int(suite.get('tests','0'))
    assert all(int(suite.get(field,'0'))==0 for field in ['failures','errors','skipped']), f'Fallo u omision en {report}'
assert required <= seen, f'Suites criticas faltantes: {required-seen}'
assert total >= 68, f'Cantidad inesperada de pruebas: {total}'
print(f'{total} pruebas verificadas, cero fallos y cero omitidas. Suites criticas presentes.')
