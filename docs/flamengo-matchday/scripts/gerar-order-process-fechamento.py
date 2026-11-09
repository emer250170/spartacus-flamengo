#!/usr/bin/env python3
"""Gera order-process-fechamento.groovy (instalador pelo HAC) a partir de order-process-fechamento.xml e do .tpl.
O passo vai em base64 porque o filtro de XSS do HAC apaga as tags de script do texto do Groovy."""
import base64, os
aqui = os.path.dirname(os.path.abspath(__file__))
xml = open(os.path.join(aqui, 'order-process-fechamento.xml'), encoding='utf-8').read()
passo = xml[xml.index('<scriptAction'):].strip('\n') + '\n'
tpl = open(os.path.join(aqui, 'order-process-fechamento.groovy.tpl'), encoding='utf-8').read()
out = tpl.replace('@@PASSO@@', base64.b64encode(passo.encode('utf-8')).decode('ascii'))
open(os.path.join(aqui, 'order-process-fechamento.groovy'), 'w', encoding='utf-8').write(out)
print('ok', len(out))
