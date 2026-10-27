// =====================================================================
//  Cria (ou atualiza) o template de jogo no Maracanã — poltrona como produto
//  Rodar em: HAC › Console › Scripting Languages (Groovy, modo "commit")
//
//  - dimensões de variante Setor > Poltrona nas versões Staged e Online
//  - produto FLA-TEMPLATE-MARACANA só na Staged, não aprovado (nunca vai
//    para a loja), com uma variante por poltrona e o preço base do setor
//  Para o mapa real do estádio, trocar SETORES/FILEIRAS/POLTRONAS abaixo
//  (ou ler de um arquivo da bilheteria) — o restante do processo é igual.
// =====================================================================
import de.hybris.platform.servicelayer.impex.ImportConfig
import de.hybris.platform.servicelayer.impex.impl.StreamBasedImpExResource

// ---------- parâmetros do template ----------
def SETORES = [
  // código     categoria de valor     nome                preço base (R$)
  [cod: 'NORTE', cat: 'setor-norte',     nome: 'Norte',          preco: 80],
  [cod: 'SUL',   cat: 'setor-sul',       nome: 'Sul',            preco: 80],
  [cod: 'LESTE', cat: 'setor-leste-inf', nome: 'Leste Inferior', preco: 160],
  [cod: 'OESTE', cat: 'setor-oeste-sup', nome: 'Oeste Superior', preco: 220],
]
def FILEIRAS = ('A'..'H')
def POLTRONAS = (1..12)
def TEMPLATE = 'FLA-TEMPLATE-MARACANA'
// ---------------------------------------------

def L = []
L << '$productCatalog=flamengoProductCatalog'
['Staged', 'Online'].each { v ->
  L << "\$catalogVersion=catalogversion(catalog(id[default=\$productCatalog]),version[default='${v}'])[unique=true,default=\$productCatalog:${v}]"
  L << '$supercategories=supercategories(code,$catalogVersion)'
  L << "INSERT_UPDATE VariantCategory;code[unique=true];\$catalogVersion;name[lang=pt];name[lang=en];\$supercategories;hasImage;allowedPrincipals(uid)[default='customergroup']"
  L << ';setor;;Setor;Sector;;false'
  L << ';poltrona;;Poltrona;Seat;setor;false'
  L << "INSERT_UPDATE VariantValueCategory;code[unique=true];\$catalogVersion;name[lang=pt];name[lang=en];\$supercategories;sequence;allowedPrincipals(uid)[default='customergroup']"
  int seq = 0
  FILEIRAS.each { f -> POLTRONAS.each { n ->
    seq++
    L << ";poltrona-${f}${String.format('%02d', n)};;${f}${n};${f}${n};poltrona;${seq}"
  } }
}
L << "\$catalogVersion=catalogversion(catalog(id[default=\$productCatalog]),version[default='Staged'])[unique=true,default=\$productCatalog:Staged]"
L << '$supercategories=supercategories(code,$catalogVersion)'
L << '$baseProduct=baseProduct(code,$catalogVersion)'
L << 'INSERT_UPDATE Product;code[unique=true];$catalogVersion;name[lang=pt];summary[lang=pt];description[lang=pt];$supercategories;variantType(code);unit(code)[default=pieces];approvalstatus(code)'
L << ";${TEMPLATE};;Template de jogo · Maracanã (não vender);Template · Maracanã;Mapa de setores e poltronas do Maracanã com o preço base de cada setor. Usado pelo script abrir-jogo para criar cada partida.;setor,poltrona;GenericVariantProduct;;check"
L << 'INSERT_UPDATE GenericVariantProduct;code[unique=true];$catalogVersion;$baseProduct;name[lang=pt];$supercategories;unit(code)[default=pieces];approvalstatus(code)'
SETORES.each { s -> FILEIRAS.each { f -> POLTRONAS.each { n ->
  def p = "${f}${String.format('%02d', n)}"
  L << ";${TEMPLATE}-${s.cod}-${p};;${TEMPLATE};Template · ${s.nome} · ${f}${n};${s.cat},poltrona-${p};;check"
} } }
L << 'INSERT_UPDATE PriceRow;productId[unique=true];unit(code[unique=true,default=pieces]);currency(isocode)[unique=true];price;minqtd;unitFactor;net'
SETORES.each { s -> FILEIRAS.each { f -> POLTRONAS.each { n ->
  L << ";${TEMPLATE}-${s.cod}-${f}${String.format('%02d', n)};;BRL;${s.preco};1;1;false"
} } }

def cfg = new ImportConfig()
cfg.script = new StreamBasedImpExResource(new ByteArrayInputStream(L.join('\n').getBytes('UTF-8')), 'UTF-8')
cfg.synchronous = true
cfg.legacyMode = false
def res = spring.getBean('importService').importData(cfg)
def total = SETORES.size() * FILEIRAS.size() * POLTRONAS.size()
if (!res.successful) {
  def erros = res.unresolvedLines ? new String(spring.getBean('mediaService').getDataFromMedia(res.unresolvedLines), 'UTF-8') : '(sem detalhe)'
  return "ERRO na importação:\n" + erros.take(3000)
}
return "Template ${TEMPLATE} pronto: ${total} poltronas"
