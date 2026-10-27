// =====================================================================
//  matchdayBaixaEstoque — baixa o estoque dos pedidos concluídos
//  Roda como CronJob (ScriptingJob "matchdayBaixaEstoqueJob", a cada minuto)
//
//  Contexto: na demo o processo de pedido (order-process, versão dinâmica)
//  pula a separação no depósito, que falhava porque os operadores de exemplo
//  estão com login bloqueado. Sem essa etapa, nada baixava o estoque; esta
//  rotina faz isso: para cada pedido COMPLETED ainda não marcado, tira do
//  estoque (warehouse_s) a quantidade de cada item e marca o pedido.
//  Poltrona vendida = estoque 0 = aparece ocupada no mapa da jornada.
// =====================================================================
import de.hybris.platform.core.Registry
import de.hybris.platform.cronjob.enums.CronJobResult
import de.hybris.platform.cronjob.enums.CronJobStatus
import de.hybris.platform.servicelayer.cronjob.PerformResult
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def ctx = Registry.applicationContext
def fs = ctx.getBean('flexibleSearchService')
def ms = ctx.getBean('modelService')
def MARCA = 'matchday:estoque-baixado'
def DEPOSITO = 'warehouse_s'

def pedidos = fs.search(new FlexibleSearchQuery(
  "SELECT {o.pk} FROM {Order AS o JOIN OrderStatus AS s ON {o.status}={s.pk}} " +
  "WHERE {s.code}='COMPLETED' AND ({o.statusInfo} IS NULL OR {o.statusInfo}<>?m)", [m: MARCA])).result

pedidos.each { o ->
  o.entries.each { e ->
    def niveis = fs.search(new FlexibleSearchQuery(
      "SELECT {pk} FROM {StockLevel} WHERE {productCode}=?c", [c: e.product.code])).result
    def nivel = niveis.find { it.warehouse?.code == DEPOSITO } ?: (niveis ? niveis[0] : null)
    if (nivel) {
      nivel.available = Math.max(0, nivel.available - (e.quantity as int))
      ms.save(nivel)
    }
  }
  o.statusInfo = MARCA
  ms.save(o)
}
return new PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED)
