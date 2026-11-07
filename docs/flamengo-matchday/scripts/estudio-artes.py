#!/usr/bin/env python3
"""Mengo Estúdio (07/11): gera o Groovy que grava as artes das estampas no Commerce.

Lê docs/flamengo-matchday/estudio/estampa-artes.json ([codigo da estampa, arquivo]) e as imagens em
estudio/artes/<arquivo>.webp; para cada uma cria (ou atualiza) a mídia "mengo-arte-<arquivo>"
(CatalogUnawareMedia, image/webp) e liga em MengoEstampa.art. Rodar no HAC depois da ImpEx 15.

Uso: python3 estudio-artes.py [--aplicar] --saida /tmp/estudio-artes.groovy
Sem --aplicar o Groovy só lista o que faria (nenhuma gravação).
"""
import argparse, base64, json, os

AQUI = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(AQUI, '..', 'estudio')

p = argparse.ArgumentParser()
p.add_argument('--aplicar', action='store_true')
p.add_argument('--saida', required=True)
a = p.parse_args()

pares = json.load(open(os.path.join(BASE, 'estampa-artes.json')))
linhas = []
for codigo, arquivo in pares:
    dados = open(os.path.join(BASE, 'artes', arquivo + '.webp'), 'rb').read()
    b64 = base64.b64encode(dados).decode()
    # o Groovy não aceita literal com mais de 65535 caracteres: a imagem vai em pedaços
    partes = ', '.join('"%s"' % b64[i:i + 60000] for i in range(0, len(b64), 60000))
    linhas.append('  ["%s", "%s", [%s].join("")]' % (codigo, arquivo, partes))

groovy = '''// Mengo Estúdio: artes das estampas (gerado por estudio-artes.py)
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
def APLICAR = %s
def ARTES = [
%s
]
def out = new StringBuilder()
def ms = spring.getBean('modelService')
def media = spring.getBean('mediaService')
def fs = spring.getBean('flexibleSearchService')
def acha = { tipo, codigo ->
  def q = new FlexibleSearchQuery("SELECT {pk} FROM {" + tipo + "} WHERE {code} = ?c")
  q.addQueryParameter('c', codigo)
  def r = fs.search(q).result
  r ? r[0] : null
}
ARTES.each { a ->
  def estampa = acha('MengoEstampa', a[0])
  if (estampa == null) { out << a[0] << ': estampa não encontrada\\n'; return }
  def bytes = a[2].decodeBase64()
  def codigo = 'mengo-arte-' + a[1]
  def m = acha('CatalogUnawareMedia', codigo)
  out << a[0] << ' <- ' << codigo << ' (' << bytes.length << ' bytes, ' << (m ? 'atualiza' : 'nova') << ')'
  if (APLICAR) {
    if (m == null) { m = ms.create('CatalogUnawareMedia'); m.code = codigo }
    m.mime = 'image/webp'
    m.realFileName = a[1] + '.webp'
    ms.save(m)
    media.setDataForMedia(m, bytes)
    ms.setAttributeValue(estampa, 'art', m)
    ms.save(estampa)
    out << ' ok ' << m.URL
  }
  out << '\\n'
}
return out.toString()
''' % ('true' if a.aplicar else 'false', ',\n'.join(linhas))
open(a.saida, 'w').write(groovy)
print('%d artes, %d KB -> %s' % (len(pares), len(groovy) // 1024, a.saida))
