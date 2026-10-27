// =====================================================================
//  FECHAR JOGO — depois da partida (ou para tirar um jogo de venda)
//  Rodar em: HAC › Console › Scripting Languages (Groovy, modo "commit")
//
//  1. tira o jogo da loja (desaprova e remove da categoria de jogos)
//  2. apaga as poltronas NÃO vendidas (com preço e estoque) e as tira de
//     carrinhos abandonados
//  3. mantém as poltronas VENDIDAS: os pedidos apontam para elas (histórico,
//     reembolso, atendimento, relatórios). Elas saem depois, junto com os
//     pedidos antigos, pela política de retenção de dados.
//  Se nenhuma poltrona foi vendida, o produto do jogo também é apagado.
// =====================================================================
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.catalog.enums.ArticleApprovalStatus

// ---------- parâmetros ----------
def CODIGO_JOGO = 'JOGO-20261019-PAL'
def CATEGORIA_LOJA = 'flamengo_jogos_casa'
def VERSOES = ['Online', 'Staged']   // procura o jogo nas duas versões
def REINDEXAR = true
// ---------------------------------

def fs = spring.getBean('flexibleSearchService')
def ms = spring.getBean('modelService')
def cvs = spring.getBean('catalogVersionService')
def q = { String fsq, Map p -> fs.search(new FlexibleSearchQuery(fsq, p)).result }

def removeDadosDoProduto = { String code ->
  ms.removeAll(q("SELECT {pk} FROM {PriceRow} WHERE {productId}=?c", [c: code]))
  ms.removeAll(q("SELECT {pk} FROM {StockLevel} WHERE {productCode}=?c", [c: code]))
}

def relatorio = []
VERSOES.each { versao ->
  def cv = cvs.getCatalogVersion('flamengoProductCatalog', versao)
  def jogo = q("SELECT {pk} FROM {Product} WHERE {code}=?c AND {catalogVersion}=?cv", [c: CODIGO_JOGO, cv: cv])[0]
  if (!jogo) { return }

  // 1. sai da loja
  jogo.approvalStatus = ArticleApprovalStatus.UNAPPROVED
  jogo.supercategories = jogo.supercategories.findAll { it.code != CATEGORIA_LOJA }
  ms.save(jogo)

  // 2 e 3. poltronas
  def vendidas = 0, apagadas = 0, carrinhos = 0
  q("SELECT {pk} FROM {VariantProduct} WHERE {baseProduct}=?b", [b: jogo]).each { poltrona ->
    def emPedido = q("SELECT {pk} FROM {OrderEntry} WHERE {product}=?p", [p: poltrona])
    if (emPedido) {
      vendidas++
      poltrona.approvalStatus = ArticleApprovalStatus.UNAPPROVED
      ms.save(poltrona)
    } else {
      q("SELECT {pk} FROM {CartEntry} WHERE {product}=?p", [p: poltrona]).each { e ->
        def cart = e.order
        ms.remove(e)
        cart.calculated = false
        ms.save(cart)
        carrinhos++
      }
      removeDadosDoProduto(poltrona.code)
      ms.remove(poltrona)
      apagadas++
    }
  }

  def jogoApagado = false
  if (vendidas == 0 && !q("SELECT {pk} FROM {OrderEntry} WHERE {product}=?p", [p: jogo])) {
    q("SELECT {pk} FROM {CartEntry} WHERE {product}=?p", [p: jogo]).each { ms.remove(it) }
    removeDadosDoProduto(jogo.code)
    ms.remove(jogo)
    jogoApagado = true
  }
  relatorio << "${versao}: ${vendidas} poltronas vendidas mantidas, ${apagadas} não vendidas apagadas" +
    (carrinhos ? ", ${carrinhos} itens tirados de carrinhos" : '') +
    (jogoApagado ? ', produto do jogo apagado' : ', produto do jogo desativado')
}

if (!relatorio) { return "Jogo ${CODIGO_JOGO} não encontrado" }
if (REINDEXAR) {
  def cs = spring.getBean('cronJobService')
  cs.performCronJob(cs.getCronJob('full-electronicsIndex-cronJob'), false)
}
return "Jogo ${CODIGO_JOGO} fechado.\n" + relatorio.join('\n')
