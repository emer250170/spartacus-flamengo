// =====================================================================
//  ABRIR JOGO — copia o template do Maracanã para uma partida nova
//  Rodar em: HAC › Console › Scripting Languages (Groovy, modo "commit")
//
//  Cria direto na versão Online (sem passar pela Staged, para não dobrar o
//  volume): o produto do jogo + uma variante por poltrona, com preço e
//  estoque 1. Depois dispara a reindexação da busca (só o produto do jogo
//  é indexado; as poltronas não entram no índice).
// =====================================================================
import de.hybris.platform.servicelayer.impex.ImportConfig
import de.hybris.platform.servicelayer.impex.impl.StreamBasedImpExResource
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

// ---------- parâmetros do jogo ----------
def JOGO = [
  codigo     : 'JOGO-20261019-PAL',            // JOGO-aaaammdd-ADV (a data ordena os jogos na loja)
  adversario : 'Palmeiras',
  data       : '19 OUT',
  hora       : '20h00',
  competicao : 'Brasileirão · Rodada 31',
  estadio    : 'Maracanã',
  dataEn     : '19 OCT',
  horaEn     : '8pm',
  competicaoEn: 'Brasileirão · Round 31',
  preVenda   : false,                           // true = selo "Pré-venda sócio"
  precos     : [:],                             // ex.: [LESTE: 180] sobrescreve o preço do template
  // simulação de vendas já feitas (% de poltronas vendidas por setor) — só para a demo
  vendidas   : [NORTE: 92, SUL: 40, LESTE: 35, OESTE: 25],
]
def TEMPLATE = 'FLA-TEMPLATE-MARACANA'
def CATEGORIA_LOJA = 'flamengo_jogos_casa'
def REINDEXAR = true
// ------------------------------------------

def fs = spring.getBean('flexibleSearchService')
def cvs = spring.getBean('catalogVersionService')
def staged = cvs.getCatalogVersion('flamengoProductCatalog', 'Staged')

def tpl = fs.search(new FlexibleSearchQuery(
  "SELECT {pk} FROM {Product} WHERE {code}=?c AND {catalogVersion}=?cv", [c: TEMPLATE, cv: staged])).result[0]
if (!tpl) { return "Template ${TEMPLATE} não encontrado — rode criar-template.groovy antes" }

def variantes = fs.search(new FlexibleSearchQuery(
  "SELECT {pk} FROM {GenericVariantProduct} WHERE {baseProduct}=?b", [b: tpl])).result
def precoTemplate = fs.search(new FlexibleSearchQuery(
  "SELECT {pk} FROM {PriceRow} WHERE {productId} LIKE ?p", [p: TEMPLATE + '-%'])).result
  .collectEntries { [(it.productId): it.price] }

def titulo = "Flamengo x ${JOGO.adversario}"
def nomePt = "${titulo} · ${JOGO.data} · ${JOGO.hora}"
def nomeEn = "${titulo} · ${JOGO.dataEn ?: JOGO.data} · ${JOGO.horaEn ?: JOGO.hora}"
def resumoPt = "${JOGO.competicao} · ${JOGO.estadio}"
def resumoEn = "${JOGO.competicaoEn ?: JOGO.competicao} · ${JOGO.estadio}"
def descricao = "${titulo}, ${JOGO.data}, ${JOGO.hora}, no ${JOGO.estadio}." +
  (JOGO.preVenda ? ' Pré-venda para sócios.' : '') +
  ' Escolha setor e poltrona e monte o seu dia de jogo: estacionamento, comidas e bebidas e a camisa nova no mesmo pedido.'
def limpa = { String s -> s.replace(';', ',') }

def L = []
L << '$productCatalog=flamengoProductCatalog'
L << "\$catalogVersion=catalogversion(catalog(id[default=\$productCatalog]),version[default='Online'])[unique=true,default=\$productCatalog:Online]"
L << '$supercategories=supercategories(code,$catalogVersion)'
L << '$baseProduct=baseProduct(code,$catalogVersion)'
L << 'INSERT_UPDATE Product;code[unique=true];$catalogVersion;name[lang=pt];name[lang=en];summary[lang=pt];summary[lang=en];description[lang=pt];$supercategories;variantType(code);unit(code)[default=pieces];approvalstatus(code)'
L << ";${JOGO.codigo};;${limpa(nomePt)};${limpa(nomeEn)};${limpa(resumoPt)};${limpa(resumoEn)};${limpa(descricao)};${CATEGORIA_LOJA},setor,poltrona;GenericVariantProduct;;approved"

def linhasVar = []
def linhasPreco = []
def linhasEstoque = []
def vendidasTotal = 0
def precoMin = null
variantes.sort(false) { it.code }.each { v ->
  def sufixo = v.code.substring(TEMPLATE.length() + 1)          // ex.: LESTE-D04
  def setor = sufixo.split('-')[0]
  def cats = v.supercategories*.code.join(',')
  def setorNome = v.supercategories.find { it.code.startsWith('setor-') }?.getName(new Locale('pt'))
  def poltrona = v.supercategories.find { it.code.startsWith('poltrona-') }?.getName(new Locale('pt'))
  def codigo = "${JOGO.codigo}-${sufixo}"
  def preco = JOGO.precos[setor] ?: precoTemplate[v.code] ?: 0
  precoMin = precoMin == null ? preco : Math.min(precoMin, preco)
  // ocupação simulada, estável para o mesmo jogo/poltrona
  def vendida = Math.abs((JOGO.codigo + sufixo).hashCode()) % 100 < (JOGO.vendidas[setor] ?: 0)
  if (vendida) { vendidasTotal++ }
  linhasVar << ";${codigo};;${JOGO.codigo};${limpa(titulo)} · ${setorNome} · ${poltrona};${cats};;approved"
  linhasPreco << ";${codigo};;BRL;${preco};1;1;false"
  linhasEstoque << ";${codigo};;${vendida ? 0 : 1};0;0;0;0;notSpecified"
}
L << 'INSERT_UPDATE GenericVariantProduct;code[unique=true];$catalogVersion;$baseProduct;name[lang=pt];$supercategories;unit(code)[default=pieces];approvalstatus(code)'
L.addAll(linhasVar)
L << 'INSERT_UPDATE PriceRow;productId[unique=true];unit(code[unique=true,default=pieces]);currency(isocode)[unique=true];price;minqtd;unitFactor;net'
L << ";${JOGO.codigo};;BRL;${precoMin};1;1;false"                 // "a partir de" no card da loja
L.addAll(linhasPreco)
L << 'INSERT_UPDATE StockLevel;productCode[unique=true];warehouse(code)[unique=true,default=warehouse_s];available;reserved;preOrder;maxPreOrder;maxStockLevelHistoryCount;inStockStatus(code)'
L << ";${JOGO.codigo};;${variantes.size() - vendidasTotal};0;0;0;0;notSpecified"  // só para o card não aparecer esgotado
L.addAll(linhasEstoque)

def cfg = new ImportConfig()
cfg.script = new StreamBasedImpExResource(new ByteArrayInputStream(L.join('\n').getBytes('UTF-8')), 'UTF-8')
cfg.synchronous = true
cfg.legacyMode = false
def res = spring.getBean('importService').importData(cfg)
if (!res.successful) {
  def erros = res.unresolvedLines ? new String(spring.getBean('mediaService').getDataFromMedia(res.unresolvedLines), 'UTF-8') : '(sem detalhe)'
  return "ERRO ao abrir ${JOGO.codigo}:\n" + erros.take(3000)
}
if (REINDEXAR) {
  def cs = spring.getBean('cronJobService')
  cs.performCronJob(cs.getCronJob('full-electronicsIndex-cronJob'), false)
}
return "Jogo ${JOGO.codigo} aberto: ${variantes.size()} poltronas, ${variantes.size() - vendidasTotal} à venda" +
  (REINDEXAR ? ' (reindexação disparada)' : '')
