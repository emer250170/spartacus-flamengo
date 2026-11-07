#!/usr/bin/env python3
"""Gera o Groovy dos e-mails da Loja Flamengo para o HAC (prévia, aplicar ou restaurar).

Cada e-mail = modelos/_base.vm (moldura, cores e macros comuns) + modelos/<arquivo>.vm (conteúdo).

    python3 gerar.py --modo previa  [--so OrderConfirmationEmailTemplate,...] [--pedido 00045000] --saida /tmp/previa.groovy
    python3 gerar.py --modo aplicar   --saida /tmp/aplicar.groovy     # rodar com commit=true
    python3 gerar.py --modo restaurar --saida /tmp/restaurar.groovy   # rodar com commit=true
    python3 gerar.py --html-dir /tmp/vm                               # só grava os .vm completos, para conferir
"""
import argparse, base64, json, pathlib

AQUI = pathlib.Path(__file__).parent
SUF = ' · Loja Flamengo'

# lista dos e-mails (também lida pelo navegador, que monta o mesmo Groovy a partir do GitHub)
EMAILS = [(e['modelo'], e['original'], e['codigo'], e['arquivo'], e['assunto'], e['processo'])
          for e in json.loads((AQUI / 'emails.json').read_text(encoding='utf-8'))]


def corpo(arquivo):
    base = (AQUI / 'modelos' / '_base.vm').read_text(encoding='utf-8')
    conteudo = (AQUI / 'modelos' / (arquivo + '.vm')).read_text(encoding='utf-8')
    return base + conteudo


def gs(s):
    """String Groovy entre aspas simples (sem interpolação)."""
    return "'" + s.replace('\\', '\\\\').replace("'", "\\'") + "'"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--modo', choices=['previa', 'aplicar', 'restaurar'])
    ap.add_argument('--pedido', default='00045000')
    ap.add_argument('--so', default='')
    ap.add_argument('--saida')
    ap.add_argument('--html-dir')
    a = ap.parse_args()
    if a.html_dir:
        d = pathlib.Path(a.html_dir); d.mkdir(parents=True, exist_ok=True)
        for e in EMAILS:
            (d / (e[3] + '.vm')).write_text(corpo(e[3]), encoding='utf-8')
        print(d)
        return
    itens = []
    for uid, orig, novo, arq, assunto, proc in EMAILS:
        b64 = base64.b64encode(corpo(arq).encode('utf-8')).decode()
        itens.append('[' + ', '.join([gs(uid), gs(orig + '_Body'), gs(orig + '_Subject'), gs(novo), gs(assunto), gs(b64),
                                      gs(proc.replace('@PEDIDO@', a.pedido))]) + ']')
    g = (AQUI / 'emails-aplicar.groovy').read_text(encoding='utf-8')
    g = g.replace('@MODO@', a.modo).replace('@PEDIDO@', a.pedido).replace('@SO@', a.so).replace('@EMAILS@', '[\n  ' + ',\n  '.join(itens) + '\n]')
    pathlib.Path(a.saida).write_text(g, encoding='utf-8')
    print(a.saida, len(g), 'bytes,', 'commit=' + ('false' if a.modo == 'previa' else 'true'))


if __name__ == '__main__':
    main()
