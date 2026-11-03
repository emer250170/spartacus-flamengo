/*
 * Item 12 do plano de evolução (retirada na loja): nova versão do order-process com o passo
 * sourcePickup entre takePayment e completeOrder.
 *
 * O passo pega as linhas do pedido com loja de retirada (deliveryPointOfService), agrupa por
 * loja e cria a remessa (Consignment) de cada loja pelo OMS (allocationService, que reserva o
 * estoque da loja e abre o fluxo de tarefas do atendente) e inicia o consignment-process padrão
 * (caminho de retirada: separar, embalar = pronto para retirada, confirmar retirada no cockpit
 * Expedição de pedidos). As outras linhas (entrega em casa, ingresso, plano, serviços) seguem
 * como hoje. Falha aqui não impede o pedido: fica no log.
 * Chave: flamengo.retirada.ativo (false = não cria remessa; o pedido segue como hoje).
 *
 * Rodar no HAC (Groovy) com commit=true. O platform cria a versão nova ao salvar o conteúdo da
 * definição ativa; a anterior fica salva em /backup/order-process-v<versão>-antes-retirada.xml.
 * Para voltar: ativar a versão anterior em Backoffice > Sistema > Processos de negócio >
 * Definições de processo dinâmicas (código order-process).
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def PASSO = '''
<!-- Item 12: remessa de retirada na loja (consignment do OMS) para as linhas com loja -->
<scriptAction id='sourcePickup'>
    <script type='groovy'><![CDATA[
import de.hybris.platform.core.Registry
import de.hybris.platform.util.Config
import de.hybris.platform.warehousing.data.sourcing.SourcingResult
import de.hybris.platform.warehousing.data.sourcing.SourcingResults
def log = org.slf4j.LoggerFactory.getLogger('flamengo-retirada')
def o = process.order
try {
  if (Config.getBoolean('flamengo.retirada.ativo', true) && (o.consignments == null || o.consignments.isEmpty())) {
    def ctx = Registry.applicationContext
    def byPos = o.entries.findAll { it.deliveryPointOfService != null && (it.quantity ?: 0) > 0 }.groupBy { it.deliveryPointOfService }
    if (byPos) {
      def rs = new SourcingResults()
      rs.results = new HashSet()
      rs.complete = true
      byPos.each { pos, es ->
        def w = pos.warehouses.find { !it.external && it.code == pos.name } ?: pos.warehouses.find { !it.external }
        if (w == null) {
          log.warn('Pedido ' + o.code + ': loja ' + pos.name + ' sem depósito; a retirada fica sem remessa')
        } else {
          def r = new SourcingResult()
          r.warehouse = w
          r.allocation = new HashMap(es.collectEntries { [(it): it.quantity] })
          rs.results.add(r)
        }
      }
      if (!rs.results.isEmpty()) {
        def bps = ctx.getBean('businessProcessService')
        def ms = ctx.getBean('modelService')
        def cons = ctx.getBean('allocationService').createConsignments(o, 'cons' + o.code, rs)
        for (c in cons) {
          def p = bps.createProcess(c.getCode() + '_ordermanagement', 'consignment-process')
          p.setParentProcess(process)
          p.setConsignment(c)
          ms.save(p)
          bps.startProcess(p)
          log.info('Pedido ' + o.code + ': remessa ' + c.getCode() + ' para retirada em ' + c.getDeliveryPointOfService()?.getName())
        }
      }
    }
  }
} catch (Exception ex) {
  log.error('Pedido ' + o.code + ': falha ao criar a remessa de retirada', ex)
}
return 'OK'
]]></script>
    <transition name='OK' to='completeOrder'/>
</scriptAction>
'''

def out = []
def proc = flexibleSearchService.search(new FlexibleSearchQuery("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")).result[0]
if (proc.content.contains("id='sourcePickup'")) {
	return "order-process: versão ${proc.version} já tem o passo sourcePickup"
}
def DE = "<action id='takePayment' bean='takePaymentAction'>"
def i = proc.content.indexOf(DE)
def j = i < 0 ? -1 : proc.content.indexOf("to='completeOrder'", i)
def k = proc.content.indexOf("<scriptAction id='completeOrder'>")
if (i < 0 || j < 0 || k < 0 || j > k) {
	throw new IllegalStateException('takePayment -> completeOrder não encontrado no order-process ativo')
}
def bk = new File('/backup'); bk.mkdirs()
def f = new File(bk, "order-process-v${proc.version}-antes-retirada.xml")
f.setText(proc.content, 'UTF-8')
def anterior = proc.version
def novo = proc.content.substring(0, j) + "to='sourcePickup'" + proc.content.substring(j + "to='completeOrder'".length())
k = novo.indexOf("<scriptAction id='completeOrder'>")
novo = novo.substring(0, k) + PASSO.trim() + '\n\n    ' + novo.substring(k)
proc.content = novo
modelService.save(proc)
def ativo = flexibleSearchService.search(new FlexibleSearchQuery("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")).result[0]
out << "order-process: versão ${ativo.version} ativa (anterior ${anterior}, backup ${f.path})"
out << 'takePayment -> ' + (ativo.content =~ /<action id='takePayment'[\s\S]*?<transition name='OK' to='([^']+)'/)[0][1]
out << 'sourcePickup presente: ' + ativo.content.contains("id='sourcePickup'")
return out.join('\n')
