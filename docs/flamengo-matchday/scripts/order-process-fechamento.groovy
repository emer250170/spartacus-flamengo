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
 *  'destravar' (commit=true): para o pedido PEDIDO parado no waitForOrderAction com as remessas terminadas, passa o
 *              processo para a versão ativa e dispara o OrderActionEvent (o passo novo decide).
 * Para voltar: ativar a versão anterior em Backoffice › Sistema › Processos de negócio › Definições de processo
 * dinâmicas (código order-process).
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.basecommerce.enums.ConsignmentStatus

def ETAPA = 'conferir'
def PEDIDO = '00057001'
// o passo vai em base64: o HAC tira do texto enviado as tags de script do HTML (proteção contra XSS)
def PASSO = new String('PHNjcmlwdEFjdGlvbiBpZD0ndmVyaWZ5T3JkZXJDb21wbGV0aW9uJz4KICAgIDxzY3JpcHQgdHlwZT0nZ3Jvb3Z5Jz48IVtDREFUQVsKaW1wb3J0IGRlLmh5YnJpcy5wbGF0Zm9ybS5jb3JlLlJlZ2lzdHJ5CmltcG9ydCBkZS5oeWJyaXMucGxhdGZvcm0uYmFzZWNvbW1lcmNlLmVudW1zLkNvbnNpZ25tZW50U3RhdHVzCmltcG9ydCBkZS5oeWJyaXMucGxhdGZvcm0uY29yZS5lbnVtcy5EZWxpdmVyeVN0YXR1cwpkZWYgY3R4ID0gUmVnaXN0cnkuYXBwbGljYXRpb25Db250ZXh0CmRlZiBsb2cgPSBvcmcuc2xmNGouTG9nZ2VyRmFjdG9yeS5nZXRMb2dnZXIoJ2ZsYW1lbmdvLWV4cGVkaWNhbycpCi8vIHJlZ3JhIHBhZHLDo28gZG8gT01TIChWZXJpZnlPcmRlckNvbXBsZXRpb25BY3Rpb24pOiBncmF2YSBhIHNpdHVhw6fDo28gZGUgZW50cmVnYSwgQ0FOQ0VMTEVEIHNlIHR1ZG8gZm9pCi8vIGNhbmNlbGFkbywgV0FJVCBzZSBhbGd1bWEgbGluaGEgbsOjbyBmb2kgYWxvY2FkYSBhIHVtYSByZW1lc3NhIG91IHNlIHVtIHN1YnByb2Nlc3NvIGRlIHJlbWVzc2EgbsOjbyB0ZXJtaW5vdQpkZWYgcmVzdWx0YWRvID0gY3R4LmdldEJlYW4oJ3ZlcmlmeU9yZGVyQ29tcGxldGlvbkFjdGlvbicpLmV4ZWN1dGUocHJvY2VzcykKaWYgKHJlc3VsdGFkbyAhPSAnV0FJVCcgfHwgIWN0eC5nZXRCZWFuKCdjb25maWd1cmF0aW9uU2VydmljZScpLmNvbmZpZ3VyYXRpb24uZ2V0Qm9vbGVhbignZmxhbWVuZ28ubG9qYS5vcGVyYWNhby5mZWNoYXJTZW1SZW1lc3NhJywgdHJ1ZSkpIHsKICByZXR1cm4gcmVzdWx0YWRvCn0KLy8gQSByZWdyYSBwYWRyw6NvIGVzcGVyYSB0YW1iw6ltIGFzIGxpbmhhcyBxdWUgbnVuY2EgZW50cmFtIGVtIHJlbWVzc2EgKHBlcnNvbmFsaXphw6fDo28sIGNvbWlkYSwgZXN0YWNpb25hbWVudG8sCi8vIHBsYW5vLCBpbmdyZXNzbzogZmxhbWVuZ28ubG9qYS5vcGVyYWNhby5zZW1SZW1lc3NhKSwgZSBvIHBlZGlkbyBmaWNhdmEgIkVtIHByb2Nlc3NhbWVudG8iIHBhcmEgc2VtcHJlLgovLyBBcXVpIGZlY2hhIHF1YW5kbyB0b2RhcyBhcyByZW1lc3NhcyB0ZXJtaW5hcmFtLCBwZWxvIG1lbm9zIHVtYSBmb2kgZW52aWFkYSBvdSByZXRpcmFkYSwgZSBhcyBsaW5oYXMgcXVlIHTDqm0KLy8gcmVtZXNzYSBlc3TDo28gZW50cmVndWVzLiBMaW5oYSBzZW0gbmVuaHVtYSByZW1lc3NhIG7Do28gc2VndXJhIG8gcGVkaWRvIChjb21vIG5vIHBhc3NvIGNvbXBsZXRlT3JkZXIpLgpkZWYgbyA9IHByb2Nlc3Mub3JkZXIKY3R4LmdldEJlYW4oJ21vZGVsU2VydmljZScpLnJlZnJlc2gobykKZGVmIHJlbWVzc2FzID0gby5jb25zaWdubWVudHMgPzogW10KZGVmIGZpbSA9IFtDb25zaWdubWVudFN0YXR1cy5TSElQUEVELCBDb25zaWdubWVudFN0YXR1cy5QSUNLVVBfQ09NUExFVEUsIENvbnNpZ25tZW50U3RhdHVzLkNBTkNFTExFRF0KZGVmIGVudHJlZ3VlID0gW0NvbnNpZ25tZW50U3RhdHVzLlNISVBQRUQsIENvbnNpZ25tZW50U3RhdHVzLlBJQ0tVUF9DT01QTEVURV0KaWYgKHJlbWVzc2FzLmlzRW1wdHkoKSB8fCByZW1lc3Nhcy5hbnkgeyAhKGl0LnN0YXR1cyBpbiBmaW0pIH0gfHwgIXJlbWVzc2FzLmFueSB7IGl0LnN0YXR1cyBpbiBlbnRyZWd1ZSB9KSB7CiAgcmV0dXJuICdXQUlUJwp9CmlmICgocHJvY2Vzcy5jb25zaWdubWVudFByb2Nlc3NlcyA/OiBbXSkuYW55IHsgIWl0LmRvbmUgfSkgewogIHJldHVybiAnV0FJVCcKfQpkZWYgcGVuZGVudGVzID0gby5lbnRyaWVzLmZpbmRBbGwgeyBlIC0+IGUuY29uc2lnbm1lbnRFbnRyaWVzICYmIChlLnF1YW50aXR5UGVuZGluZyA/OiAwTCkgPiAwTCB9CmlmIChwZW5kZW50ZXMpIHsKICByZXR1cm4gJ1dBSVQnCn0Kby5kZWxpdmVyeVN0YXR1cyA9IERlbGl2ZXJ5U3RhdHVzLlNISVBQRUQKY3R4LmdldEJlYW4oJ21vZGVsU2VydmljZScpLnNhdmUobykKZGVmIHNlbVJlbWVzc2EgPSBvLmVudHJpZXMuZmluZEFsbCB7IGUgLT4gIWUuY29uc2lnbm1lbnRFbnRyaWVzIH0uY29sbGVjdCB7IGl0LnByb2R1Y3Q/LmNvZGUgfQpsb2cuaW5mbygnUGVkaWRvICcgKyBvLmNvZGUgKyAnOiByZW1lc3NhcyBmaW5hbGl6YWRhczsgbGluaGFzIHNlbSByZW1lc3NhICcgKyBzZW1SZW1lc3NhICsgJyBuw6NvIHNlZ3VyYW0gbyBwZWRpZG8nKQpyZXR1cm4gJ09LJwpdXT48L3NjcmlwdD4KICAgIDx0cmFuc2l0aW9uIG5hbWU9J09LJyB0bz0nZmluaXNoT3JkZXInLz4KICAgIDx0cmFuc2l0aW9uIG5hbWU9J1dBSVQnIHRvPSd3YWl0Rm9yT3JkZXJBY3Rpb24nLz4KICAgIDx0cmFuc2l0aW9uIG5hbWU9J0NBTkNFTExFRCcgdG89J3N1Y2Nlc3MnLz4KPC9zY3JpcHRBY3Rpb24+Cg=='.decodeBase64(), 'UTF-8')

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
	def pr = parados().find { it.order.code == PEDIDO }
	if (pr == null) falha('pedido ' + PEDIDO + ' não está parado no waitForOrderAction')
	if (!pr.order.consignments || !pr.order.consignments.every { it.status in fim }) falha('remessas do pedido ainda em aberto: ' + descrever(pr))
	out << 'antes: ' + descrever(pr)
	pr.processDefinitionVersion = p.version
	modelService.save(pr)
	businessProcessService.triggerEvent(pr.code + '_OrderActionEvent')
	out << "processo ${pr.code} passou para a v${p.version}; OrderActionEvent disparado (o pedido fecha em alguns segundos)"
}

return out.join('\n')
