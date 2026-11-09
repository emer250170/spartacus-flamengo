/*
 * 08/11: o pedido fecha quando todas as remessas terminaram, sem esperar as linhas que nunca têm remessa.
 * GERADO por gerar-order-process-fechamento.py a partir de order-process-fechamento.xml: não editar o .groovy
 * à mão (editar o .xml ou o .tpl e gerar de novo).
 *
 * Problema: com a operação do pedido ligada (item 14), o passo verifyOrderCompletion (VerifyOrderCompletionAction
 * do OMS) espera TODAS as linhas alocadas a uma remessa. Linhas que nunca entram em remessa (personalização,
 * comida, estacionamento, plano, ingresso: flamengo.loja.operacao.semRemessa) ficavam pendentes e o pedido parava
 * em "Em processamento" mesmo com a remessa enviada (pedido 00057001). O passo novo chama a regra padrão e, só
 * quando ela manda esperar, fecha o pedido se todas as remessas terminaram (pelo menos uma enviada ou retirada),
 * os subprocessos de remessa acabaram e as linhas que têm remessa estão entregues.
 * Chave flamengo.loja.operacao.fecharSemRemessa (padrão true; false = só a regra padrão).
 *
 * HAC › Console › Scripting (Groovy):
 *  'conferir'  (commit=false): confere a versão ativa, monta a nova, confere o XML e as transições, compila o
 *              passo e mostra os pedidos parados no waitForOrderAction. Não grava nada.
 *  'aplicar'   (commit=true): o mesmo, mais backup em /backup/order-process-v<versão>-antes-fechamento.xml e a
 *              gravação (o Commerce cria a versão nova e a ativa).
 *  'destravar' (commit=true): para o pedido PEDIDO com as remessas terminadas, passa o processo para a versão ativa e:
 *              parado no waitForOrderAction -> dispara o OrderActionEvent com a escolha consignmentProcessEnded (sem
 *              a escolha, o nó de espera vai para 'failed'); processo FAILED -> reinicia no verifyOrderCompletion.
 * Para voltar: ativar a versão anterior em Backoffice › Sistema › Processos de negócio › Definições de processo
 * dinâmicas (código order-process).
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.basecommerce.enums.ConsignmentStatus
import de.hybris.platform.processengine.enums.ProcessState
import de.hybris.platform.processengine.BusinessProcessEvent

def ETAPA = 'conferir'
def PEDIDO = '00057001'
// o passo vai em base64: o HAC tira do texto enviado as tags de script do HTML (proteção contra XSS)
def PASSO = new String('@@PASSO@@'.decodeBase64(), 'UTF-8')

def out = []
def fs = flexibleSearchService
def SA = '<' + 'scriptAction'
def ativa = { fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")).result[0] }
def falha = { String m -> throw new IllegalStateException(m) }
def fim = [ConsignmentStatus.SHIPPED, ConsignmentStatus.PICKUP_COMPLETE, ConsignmentStatus.CANCELLED]

def montar = { String c ->
	def m = (c =~ /<action id='verifyOrderCompletion' bean='verifyOrderCompletionAction'>[\s\S]*?<\/action>/)
	if (!m.find()) falha("<action id='verifyOrderCompletion' bean='verifyOrderCompletionAction'> não encontrado")
	def bloco = m.group()
	["name='OK' to='finishOrder'", "name='WAIT' to='waitForOrderAction'", "name='CANCELLED' to='success'"].each {
		if (!bloco.contains(it)) falha('transição esperada não encontrada no verifyOrderCompletion: ' + it)
	}
	return c.substring(0, m.start()) + PASSO.trim() + c.substring(m.end())
}

def conferir = { String novo ->
	def doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
			.parse(new ByteArrayInputStream(novo.getBytes('UTF-8')))
	def ids = [] as Set
	['action', 'scriptAction', 'wait', 'end', 'split', 'join', 'notify'].each { t ->
		def l = doc.getElementsByTagName(t)
		for (x in 0..<l.length) { ids << l.item(x).getAttribute('id') }
	}
	def alvos = []
	['transition': 'to', 'wait': 'then', 'timeout': 'then', 'choice': 'then'].each { t, attr ->
		def l = doc.getElementsByTagName(t)
		for (x in 0..<l.length) { alvos << l.item(x).getAttribute(attr) }
	}
	def soltos = alvos.findAll { it && !(it in ids) }.unique()
	if (soltos) falha('transições para passos que não existem: ' + soltos)
	def gcl = new GroovyClassLoader(de.hybris.platform.core.Registry.class.classLoader)
	def nos = doc.getElementsByTagName('script')
	for (x in 0..<nos.length) {
		def n = nos.item(x)
		if (n.parentNode.getAttribute('id') == 'verifyOrderCompletion') {
			gcl.parseClass(n.textContent, 'verifyOrderCompletion.groovy')
		}
	}
	out << 'XML ok, ' + ids.size() + ' passos, transições ok; verifyOrderCompletion compilado'
}

def parados = {
	fs.search(new FlexibleSearchQuery("SELECT {p.pk} FROM {OrderProcess AS p JOIN ProcessTask AS t ON {t.process}={p.pk}} " +
			"WHERE {p.processDefinitionName}='order-process' AND {t.action}='waitForOrderAction'")).result
}
def descrever = { p ->
	def o = p.order
	def rem = (o.consignments ?: []).collect { it.code + ' ' + it.status }
	def sem = o.entries.findAll { !it.consignmentEntries }.collect { it.product.code }
	"${o.code} (processo v${p.processDefinitionVersion}, pedido ${o.status}) remessas ${rem} linhas sem remessa ${sem}"
}

if (ETAPA in ['conferir', 'aplicar']) {
	def p = ativa()
	out << "order-process ativo: v${p.version}"
	if (p.content.contains(SA + " id='verifyOrderCompletion'")) {
		out << 'a versão ativa já tem o verifyOrderCompletion novo: nada a gravar'
	} else {
		def novo = montar(p.content.replace('\r\n', '\n'))
		conferir(novo)
		out << 'versão nova: ' + novo.length() + ' caracteres (era ' + p.content.length() + ')'
		if (ETAPA == 'aplicar') {
			def bk = new File('/backup'); bk.mkdirs()
			def f = new File(bk, "order-process-v${p.version}-antes-fechamento.xml")
			f.setText(p.content, 'UTF-8')
			def anterior = p.version
			p.content = novo
			modelService.save(p)
			def nova = ativa()
			out << "gravado: order-process v${nova.version} ativo (anterior v${anterior}, backup ${f.path})"
		}
	}
	def lista = parados()
	out << 'pedidos parados no waitForOrderAction: ' + lista.size()
	lista.findAll { pr -> pr.order.consignments && pr.order.consignments.every { it.status in fim } }.each { pr ->
		out << '  remessas terminadas: ' + descrever(pr)
	}
}

if (ETAPA == 'destravar') {
	def p = ativa()
	if (!p.content.contains(SA + " id='verifyOrderCompletion'")) falha('a versão ativa ainda não tem o passo novo: rode aplicar antes')
	def o = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {Order} WHERE {code}=?c AND {versionID} IS NULL", [c: PEDIDO])).result[0]
	if (o == null) falha('pedido ' + PEDIDO + ' não encontrado')
	if (!o.consignments || !o.consignments.every { it.status in fim }) falha('remessas do pedido ainda em aberto: ' + o.consignments*.status)
	def pr = parados().find { it.order.code == PEDIDO }
	if (pr != null) {
		out << 'antes: ' + descrever(pr)
		pr.processDefinitionVersion = p.version
		modelService.save(pr)
		// o nó waitForOrderAction escolhe o próximo passo pela escolha do evento; sem ela vai para 'failed'
		businessProcessService.triggerEvent(BusinessProcessEvent.builder(pr.code + '_OrderActionEvent')
				.withChoice('consignmentProcessEnded').build())
		out << "processo ${pr.code} passou para a v${p.version}; OrderActionEvent (consignmentProcessEnded) disparado"
	} else {
		pr = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {OrderProcess} WHERE {order}=?o AND {processDefinitionName}='order-process'", [o: o])).result
				.find { it.state == ProcessState.FAILED }
		if (pr == null) falha('pedido ' + PEDIDO + ' não está parado no waitForOrderAction nem com o processo em FAILED')
		out << 'antes: ' + descrever(pr) + ', processo ' + pr.state
		pr.processDefinitionVersion = p.version
		modelService.save(pr)
		businessProcessService.restartProcess(pr, 'verifyOrderCompletion')
		out << "processo ${pr.code} passou para a v${p.version} e reiniciou no verifyOrderCompletion"
	}
	out << 'o pedido fecha em alguns segundos (Completado no Backoffice, Enviado na loja)'
}

return out.join('\n')
