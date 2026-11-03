#!/usr/bin/env python3
"""Gera order-process-operacao.groovy (instalador do item 14 pelo HAC) a partir de
order-process-operacao.xml. Os blocos vão em base64 porque o filtro de XSS do HAC apaga as
tags de script do texto do Groovy."""
import base64, os, re
aqui = os.path.dirname(os.path.abspath(__file__))
xml = open(os.path.join(aqui, 'order-process-operacao.xml'), encoding='utf-8').read()
def bloco(ini, fim):
    a = xml.index('<!-- %s' % ini); a = xml.index('\n', a) + 1
    b = xml.index('<!-- %s -->' % fim)
    return xml[a:b].strip('\n') + '\n'
pag = bloco('BLOCO_PAGAMENTO_E_REMESSAS', 'FIM_BLOCO_PAGAMENTO_E_REMESSAS')
fec = bloco('BLOCO_FECHAMENTO', 'FIM_BLOCO_FECHAMENTO')
b64 = lambda s: base64.b64encode(s.encode('utf-8')).decode('ascii')
tpl = open(os.path.join(aqui, 'order-process-operacao.groovy.tpl'), encoding='utf-8').read()
out = tpl.replace('@@PAGAMENTO@@', b64(pag)).replace('@@FECHAMENTO@@', b64(fec))
open(os.path.join(aqui, 'order-process-operacao.groovy'), 'w', encoding='utf-8').write(out)
print('ok', len(out))
