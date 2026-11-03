// publica (ACAO='publicar') ou tira do ar (ACAO='despublicar') as regras de demonstracao, fora da transacao do console
def ACAO = 'publicar'
def CODIGOS = ['demo_calcados_15', 'demo_frete_gratis_299', 'demo_brinde_manto']
def regras = flexibleSearchService.search("SELECT {pk} FROM {PromotionSourceRule} WHERE {code} IN (?c)", [c: CODIGOS]).result
def pks = regras*.pk
def tenant = de.hybris.platform.core.Registry.currentTenant
def ctx = spring
def saida = new File('/backup/loja-virtual-demo-publicacao.txt')
saida.text = ACAO + ' agendado ' + new Date() + ' ' + CODIGOS + '\n'
Thread.start('loja-virtual-demo-' + ACAO) {
  de.hybris.platform.core.Registry.setCurrentTenant(tenant)
  try {
    Thread.sleep(3000)
    def ms = ctx.getBean('modelService')
    def rs = pks.collect { ms.get(it) }
    if (ACAO == 'publicar') {
      def res = ctx.getBean('ruleMaintenanceService').compileAndPublishRulesWithBlocking(rs, 'promotions-module', true)
      saida << 'publicacao: ' + res.result + ' ' + new Date() + '\n'
      rs.each { ms.refresh(it); it.status = de.hybris.platform.ruleengineservices.enums.RuleStatus.PUBLISHED; ms.save(it) }
    } else {
      def res = ctx.getBean('ruleMaintenanceService').undeployRules(rs, 'promotions-module')
      saida << 'retirada: ' + (res?.hasProperty('result') ? res.result : res) + ' ' + new Date() + '\n'
      rs.each { ms.refresh(it); saida << it.code + ':' + it.status + '\n' }
    }
  } catch (Exception e) {
    saida << 'ERRO ' + e + '\n'
  } finally {
    de.hybris.platform.core.Registry.unsetCurrentTenant()
  }
}
return ACAO + ' agendado para ' + regras*.code
