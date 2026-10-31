#!/usr/bin/env python3
"""
Gera o script Groovy de deploy da extensão flamengomatchday para rodar no HAC.

  # o que mudou entre a versão no servidor (BASE) e a nova (HEAD)
  python3 backend/hac-deploy/gerar.py --base origin/flamengo --head HEAD --rotulo pr52 --modo conferir
  python3 backend/hac-deploy/gerar.py --base origin/flamengo --head HEAD --rotulo pr52 --modo aplicar

  # auditoria: o servidor está igual ao repositório? (todos os arquivos, nada é gravado)
  python3 backend/hac-deploy/gerar.py --auditar HEAD

Saída: o Groovy em stdout (ou em --saida). Rode no HAC › Console › Scripting.
BASE deve ser a versão que está hoje no servidor (normalmente a branch flamengo antes do merge do PR).
"""
import argparse, base64, hashlib, io, json, pathlib, subprocess, sys, tarfile, time

EXT = 'backend/flamengomatchday/'
AQUI = pathlib.Path(__file__).resolve().parent


def git(*a, binario=False):
    r = subprocess.run(['git', *a], capture_output=True, check=True)
    return r.stdout if binario else r.stdout.decode()


def conteudo(ref, rel):
    return git('show', f'{ref}:{EXT}{rel}', binario=True)


def md5(b):
    return hashlib.md5(b).hexdigest()


def groovy_map(d):
    if not d:
        return '[:]'
    return '[\n' + ',\n'.join(f'  {json.dumps(k)}: {json.dumps(v)}' for k, v in d.items()) + '\n]'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base')
    ap.add_argument('--head', default='HEAD')
    ap.add_argument('--rotulo', default='deploy')
    ap.add_argument('--modo', choices=['conferir', 'aplicar'], default='conferir')
    ap.add_argument('--auditar', metavar='REF')
    ap.add_argument('--saida')
    a = ap.parse_args()

    antes, depois, novos = {}, {}, {}
    if a.auditar:
        for rel in git('ls-tree', '-r', '--name-only', a.auditar, EXT).split():
            rel = rel[len(EXT):]
            antes[rel] = md5(conteudo(a.auditar, rel))
        a.modo, a.rotulo = 'conferir', f'auditoria-{a.auditar}'
    else:
        if not a.base:
            ap.error('--base é obrigatório (ou use --auditar)')
        for linha in git('diff', '--name-status', '--no-renames', a.base, a.head, '--', EXT).splitlines():
            status, caminho = linha.split('\t', 1)
            rel = caminho[len(EXT):]
            antes[rel] = 'NOVO' if status == 'A' else md5(conteudo(a.base, rel))
            if status == 'D':
                depois[rel] = 'REMOVIDO'
            else:
                b = conteudo(a.head, rel)
                depois[rel] = md5(b)
                novos[rel] = b
        if not antes:
            sys.exit('Nenhuma mudança em backend/flamengomatchday entre BASE e HEAD.')

    pacote = ''
    if novos and a.modo == 'aplicar':
        buf = io.BytesIO()
        with tarfile.open(fileobj=buf, mode='w:gz') as t:
            for rel, b in novos.items():
                info = tarfile.TarInfo(rel)
                # data de agora: o ant all só recompila/copia o que for mais novo que o build anterior
                info.size, info.mode, info.mtime = len(b), 0o644, int(time.time())
                t.addfile(info, io.BytesIO(b))
        pacote = base64.b64encode(buf.getvalue()).decode()

    g = (AQUI / 'template.groovy').read_text()
    for k, v in {'@@MODO@@': a.modo, '@@ROTULO@@': a.rotulo, '@@ANTES@@': groovy_map(antes),
                 '@@DEPOIS@@': groovy_map(depois), '@@PACOTE@@': pacote}.items():
        g = g.replace(k, v)
    if a.saida:
        pathlib.Path(a.saida).write_text(g)
        print(f'{a.saida}: {len(antes)} arquivo(s), modo {a.modo}, pacote {len(pacote)} bytes', file=sys.stderr)
    else:
        sys.stdout.write(g)


if __name__ == '__main__':
    main()
