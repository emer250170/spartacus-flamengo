#!/usr/bin/env python3
"""Gera o Groovy de aplicação do e-mail de confirmação (com o .vm em base64) e o JS para rodar no HAC."""
import argparse, base64, json, pathlib
aqui = pathlib.Path(__file__).parent
ap = argparse.ArgumentParser()
ap.add_argument('--modo', choices=['previa', 'aplicar', 'restaurar'], required=True)
ap.add_argument('--pedido', default='00045000')
ap.add_argument('--saida', required=True)
a = ap.parse_args()
vm = (aqui / 'confirmacao-pedido-corpo.vm').read_text(encoding='utf-8')
g = (aqui / 'confirmacao-pedido-aplicar.groovy').read_text(encoding='utf-8')
g = g.replace('@MODO@', a.modo).replace('@PEDIDO@', a.pedido).replace('@CORPO_B64@', base64.b64encode(vm.encode()).decode())
commit = 'true' if a.modo != 'previa' else 'false'
js = ('async () => { const SCRIPT = ' + json.dumps(g) + "; const csrf = document.querySelector('meta[name=\"_csrf\"]').content; "
      "const r = await fetch('/console/scripting/execute', { method: 'POST', headers: { 'X-CSRF-TOKEN': csrf, 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }, "
      "body: new URLSearchParams({ script: SCRIPT, scriptType: 'groovy', commit: '" + commit + "' }) }); const j = await r.json(); return j.executionResult + (j.stacktraceText || ''); }")
pathlib.Path(a.saida).write_text(js, encoding='utf-8')
print(a.saida, len(js))
