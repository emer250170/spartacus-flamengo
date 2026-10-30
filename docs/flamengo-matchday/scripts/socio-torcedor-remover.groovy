// =====================================================================
//  SÓCIO-TORCEDOR — desfaz o socio-torcedor-cadastro.groovy
//  Rodar em: HAC › Console › Scripting Languages (Groovy, modo "commit")
//  Volta o order-process para a versão anterior, tira as promoções do ar
//  e apaga planos, preços, benefícios, grupos e o cliente de demonstração.
//  Pedidos já feitos com plano continuam no histórico.
//  (Para voltar o banco inteiro: /backup/antes-socio-torcedor-20261030/restaurar.sh)
// =====================================================================
import de.hybris.platform.core.Registry
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def ctx = Registry.applicationContext
def fs = ctx.getBean('flexibleSearchService')
def ms = ctx.getBean('modelService')
def all = { String q, Map p = [:] -> fs.search(new FlexibleSearchQuery(q, p)).result }
def log = []

// 1. order-process: reativa a versão sem o bloco do sócio
def versoes = all("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' ORDER BY {version} DESC")
def ativa = versoes.find { it.active }
if (ativa?.content?.contains('Sócio-torcedor: pedido com plano')) {
  def anterior = versoes.find { !it.active && !it.content.contains('Sócio-torcedor: pedido com plano') }
  if (anterior) {
    anterior.active = true
    ms.save(anterior)
    log << "order-process: versão ${anterior.version} reativada"
  } else {
    def ini = ativa.content.indexOf('// Sócio-torcedor: pedido com plano')
    def fim = ativa.content.indexOf('o.status = OrderStatus.COMPLETED')
    ativa.content = ativa.content.substring(0, ini) + ativa.content.substring(fim)
    ms.save(ativa)
    log << 'order-process: bloco do sócio retirado'
  }
}

// 2. promoções: tirar do ar fora da transação do console (dentro dela a troca
//    do módulo de regras trava), depois apagar
def regras = all("SELECT {pk} FROM {PromotionSourceRule} WHERE {code} LIKE 'nacao_maraca_%'")
if (regras) {
  def pks = regras*.pk
  def tenant = Registry.currentTenant
  Thread.start('socio-remove-promocoes') {
    Registry.setCurrentTenant(tenant)
    try {
      Thread.sleep(5000)
      def rs = pks.collect { ms.get(it) }
      ctx.getBean('ruleMaintenanceService').undeployRules(rs, 'promotions-module')
      rs.each { ms.remove(it) }
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger('socio-torcedor').error('Falha ao remover promoções', e)
    } finally { Registry.unsetCurrentTenant() }
  }
  log << "promoções: ${regras.size()} saindo do ar em segundo plano"
}
def cvOnline = ctx.getBean('catalogVersionService').getCatalogVersion('flamengoProductCatalog', 'Online')
all('SELECT {pk} FROM {CatalogVersionToRuleEngineContextMapping} WHERE {catalogVersion}=?cv', [cv: cvOnline]).each { ms.remove(it) }

// 3. benefícios, preços, estoque e produtos
all("SELECT {pk} FROM {ProductEntitlement} WHERE {id} LIKE 'NACAO-%'").each { ms.remove(it) }
all("SELECT {pk} FROM {Entitlement} WHERE {id} LIKE 'nacao-%'").each { ms.remove(it) }
def produtos = all("SELECT {pk} FROM {Product} WHERE {code} LIKE 'NACAO-%'")
produtos.each { p ->
  p.europe1Prices.each { pr ->
    if (pr.itemtype == 'SubscriptionPricePlan') { (pr.oneTimeChargeEntries + pr.recurringChargeEntries).each { ms.remove(it) } }
    ms.remove(pr)
  }
}
all("SELECT {pk} FROM {StockLevel} WHERE {productCode} LIKE 'NACAO-%'").each { ms.remove(it) }
produtos.each { ms.remove(it) }
log << "produtos removidos: ${produtos.size()}"
all("SELECT {pk} FROM {Category} WHERE {code}='socio_torcedor'").each { ms.remove(it) }
all("SELECT {pk} FROM {SubscriptionTerm} WHERE {id}='nacao-anual'").each { ms.remove(it) }
all("SELECT {pk} FROM {BillingPlan} WHERE {id}='nacao-mensal'").each { ms.remove(it) }

// 4. grupos e cliente de demonstração
all("SELECT {pk} FROM {Customer} WHERE {uid}='socio.teste@example.com'").each { ms.remove(it) }
all("SELECT {pk} FROM {UserGroup} WHERE {uid} LIKE 'nacao-maraca-%'").each { ms.remove(it) }
log << 'grupos nacao-maraca-* e cliente de demonstração removidos'

return log.join('\n')
