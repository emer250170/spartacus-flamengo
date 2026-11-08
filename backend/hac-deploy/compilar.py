#!/usr/bin/env python3
"""Checagem de compilação de classes Java da extensão no próprio servidor, pelo HAC (sem ant all).

Gera um Groovy que compila os arquivos .java informados numa pasta temporária, contra as bibliotecas e
classes do Commerce instalado (/opt/sap/hybris/bin), e devolve os erros. Não grava nada na extensão.

    python3 backend/hac-deploy/compilar.py backend/flamengomatchday/src/br/com/.../Classe.java ... --saida /tmp/compilar.groovy

Limite: só compila contra o que já está no servidor; classes de modelo de tipos novos do items.xml só existem
depois do ant all (para classes novas, ler e gravar atributos pelo nome com modelService.getAttributeValue).
O script passa de 50 KB com poucos arquivos: rodar pelo <input type=file> no HAC (ver CLAUDE.md).
"""
import argparse
import base64
import os

GROOVY = r'''import javax.tools.*
def files = [
%s
]
def base = new File(System.getProperty('java.io.tmpdir'), 'compilar-' + System.currentTimeMillis())
def src = new File(base, 'src'); def out = new File(base, 'out'); out.mkdirs()
def sources = []
files.each { path, b64 -> def f = new File(src, path); f.parentFile.mkdirs(); f.bytes = b64.decodeBase64(); sources << f }
def cp = new LinkedHashSet()
new File('/opt/sap/hybris/bin').eachFileRecurse { f ->
  def p = f.path
  if (p.contains('/node_modules/') || p.contains('/web/webroot/') || p.contains('/testclasses') || p.contains('/hmc/')) return
  if (f.isFile() && p.endsWith('.jar')) cp << p
  else if (f.isDirectory() && f.name == 'classes' && (p.contains('/custom/') || p.contains('/modules/') || p.contains('/platform/'))) cp << p
}
def compiler = ToolProvider.getSystemJavaCompiler()
if (compiler == null) return 'sem compilador (o servidor roda num JRE)'
def diag = new DiagnosticCollector()
def fm = compiler.getStandardFileManager(diag, null, java.nio.charset.StandardCharsets.UTF_8)
def opts = ['-proc:none', '-nowarn', '-encoding', 'UTF-8', '-d', out.path, '-classpath', cp.join(File.pathSeparator)]
def ok = compiler.getTask(null, fm, diag, opts, null, fm.getJavaFileObjectsFromFiles(sources)).call()
def msgs = diag.diagnostics.findAll{ it.kind == Diagnostic.Kind.ERROR }.collect{ (it.source?.name?.replaceAll('.*/','') ?: '') + ':' + it.lineNumber + ' ' + it.getMessage(Locale.ROOT) }
base.deleteDir()
return 'compilou=' + ok + ' classpath=' + cp.size() + ' erros=' + msgs.size() + '\n' + msgs.take(40).join('\n')
'''


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('arquivos', nargs='+', help='arquivos .java (caminho com /src/ para achar o pacote)')
    ap.add_argument('--saida', required=True, help='arquivo .groovy gerado')
    a = ap.parse_args()
    entries = []
    for path in a.arquivos:
        rel = path.split('/src/', 1)[1] if '/src/' in path else os.path.basename(path)
        with open(path, 'rb') as f:
            entries.append("'%s':'%s'" % (rel, base64.b64encode(f.read()).decode()))
    with open(a.saida, 'w', encoding='utf-8') as f:
        f.write(GROOVY % ',\n'.join(entries))
    print('%s: %d arquivo(s), %d bytes' % (a.saida, len(entries), os.path.getsize(a.saida)))


if __name__ == '__main__':
    main()
