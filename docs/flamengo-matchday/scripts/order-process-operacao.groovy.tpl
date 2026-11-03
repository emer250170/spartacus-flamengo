/*
 * Item 14 do plano de evolução (operação do pedido da loja): instalador pelo HAC.
 * GERADO por gerar-order-process-operacao.py a partir de order-process-operacao.xml: não editar
 * o .groovy à mão (editar o .xml ou o .tpl e gerar de novo).
 *
 * ETAPA:
 *  'conferir'  : confere que a versão ativa do order-process é a v6 do item 12 (md5), monta a versão
 *                nova, confere o XML e as transições e compila os scripts dos passos. Não grava nada.
 *  'aplicar'   : o mesmo, mais backup em /backup/order-process-v<versão>-antes-operacao.xml e a
 *                gravação (o platform cria a versão nova e a ativa).
 *  'atendente' : atendente do centro de distribuição (atendente.cd, sem senha: o Emerson define no
 *                Backoffice) no grupo da loja online (depósito warehouse_s) e nos grupos do armazém.
 *  'traducoes' : nomes em português dos status do pedido usados no item 14 (o inglês não muda).
 *
 * A versão nova só muda o pedido com a chave flamengo.loja.operacao.ativo=true; desligada, o pedido
 * anda como antes. Para voltar de vez: ativar a versão anterior em Backoffice > Sistema > Processos
 * de negócio > Definições de processo dinâmicas (código order-process).
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.commerceservices.model.user.StoreEmployeeGroupModel
import de.hybris.platform.core.model.user.EmployeeModel

def ETAPA = 'conferir'
def BASE_MD5 = '418f1985546d3e9d62d76fca1858fa68'   // order-process v6 (item 12)
def PAGAMENTO = new String('@@PAGAMENTO@@'.decodeBase64(), 'UTF-8')
def FECHAMENTO = new String('@@FECHAMENTO@@'.decodeBase64(), 'UTF-8')

def out = []
def fs = flexibleSearchService
def SA = '<' + 'scriptAction'          // o filtro do HAC apaga a tag escrita inteira
def SA_FIM = '</' + 'scriptAction>'
def ativa = { fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")).result[0] }
def md5 = { String t -> java.security.MessageDigest.getInstance('MD5').digest(t.getBytes('UTF-8')).encodeHex().toString() }
def falha = { String m -> throw new IllegalStateException(m) }

def montar = { String c ->
	// 1a. postTaxes OK -> checkPayment (a conferência do pagamento vem antes do takePayment, que
	//     passa o status do pedido para PAYMENT_CAPTURED)
	def h = c.indexOf("<action id='postTaxes'")
	def hj = h < 0 ? -1 : c.indexOf("to='takePayment'", h)
	if (hj < 0 || hj > c.indexOf('</action>', h)) falha('postTaxes -> takePayment não encontrado')
	c = c.substring(0, hj) + "to='checkPayment'" + c.substring(hj + "to='takePayment'".length())
	// 1b. takePayment OK -> sourceFulfillment
	def i = c.indexOf("<action id='takePayment'")
	def j = i < 0 ? -1 : c.indexOf("to='sourcePickup'", i)
	if (j < 0 || j > c.indexOf('</action>', i)) falha('takePayment -> sourcePickup não encontrado')
	c = c.substring(0, j) + "to='sourceFulfillment'" + c.substring(j + "to='sourcePickup'".length())
	// 2. bloco sourcePickup (item 12) -> pagamento e remessas
	def a = c.indexOf('<!-- Item 12: remessa de retirada')
	def b = c.indexOf(SA + " id='sourcePickup'>")
	if (a < 0 || b < a) falha('bloco sourcePickup não encontrado')
	def e = c.indexOf(SA_FIM, b) + SA_FIM.length()
	c = c.substring(0, a) + PAGAMENTO.replaceAll(/\s+$/, '') + c.substring(e)
	// 3. completeOrder -> fechamento (completeOrder + finishOrder)
	def k = c.indexOf('    ' + SA + " id='completeOrder'>")
	if (k < 0) falha('completeOrder não encontrado')
	def l = c.indexOf(SA_FIM, k) + SA_FIM.length()
	c = c.substring(0, k) + FECHAMENTO.replaceAll(/\s+$/, '') + c.substring(l)
	// 4. verifyOrderCompletion OK -> finishOrder
	def v = c.indexOf("<action id='verifyOrderCompletion'")
	def ok = "<transition name='OK' to='postTaxes'/>"
	def w = v < 0 ? -1 : c.indexOf(ok, v)
	if (w < 0 || w > c.indexOf('</action>', v)) falha('verifyOrderCompletion -> postTaxes não encontrado')
	c = c.substring(0, w) + "<transition name='OK' to='finishOrder'/>" + c.substring(w + ok.length())
	return c
}

def conferir = { String novo ->
	def doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
			.parse(new ByteArrayInputStream(novo.getBytes('UTF-8')))
	def ids = [] as Set
	['action', 'scriptAction', 'wait', 'end'].each { t ->
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
	def compilados = []
	for (x in 0..<nos.length) {
		def n = nos.item(x)
		def id = n.parentNode.getAttribute('id')
		gcl.parseClass(n.textContent, id + '.groovy')
		compilados << id
	}
	out << 'XML ok, ' + ids.size() + ' passos, transições ok; scripts compilados: ' + compilados.join(', ')
	def ok = { String id -> (novo =~ ("<(action|scriptAction) id='" + id + "'[\\s\\S]*?<transition name='OK' to='([^']+)'"))[0][2] }
	out << 'postTaxes OK -> ' + ok('postTaxes') + ' | checkPayment OK -> ' + ok('checkPayment') + ' | takePayment OK -> ' + ok('takePayment') + ' | verifyOrderCompletion OK -> ' + ok('verifyOrderCompletion')
}

if (ETAPA in ['conferir', 'aplicar']) {
	def p = ativa()
	out << "order-process ativo: v${p.version}, md5 ${md5(p.content)}"
	def base = p.content
	if (p.content.contains("id='checkPayment'")) {
		if (p.content =~ /<action id='postTaxes'[^>]*>\s*<transition name='OK' to='checkPayment'/) {
			return (out + ['a versão ativa já tem o item 14: nada a fazer']).join('\n')
		}
		// primeira versão do item 14 (03/11, conferência depois do takePayment): refaz a partir do backup da v6
		def v6 = new File('/backup/order-process-v6-antes-operacao.xml')
		if (!v6.exists()) falha('versão com checkPayment depois do takePayment e sem o backup da v6: pare e confira')
		base = v6.getText('UTF-8')
		out << 'refazendo a partir de ' + v6.path + ' (md5 ' + md5(base) + ')'
	}
	if (md5(base) != BASE_MD5) falha('a base não é a esperada (v6 do item 12, md5 ' + BASE_MD5 + '): pare e confira')
	def novo = montar(base)
	conferir(novo)
	out << 'versão nova: ' + novo.length() + ' caracteres (era ' + p.content.length() + ')'
	if (ETAPA == 'aplicar') {
		def bk = new File('/backup'); bk.mkdirs()
		def f = new File(bk, "order-process-v${p.version}-antes-operacao" + (base.is(p.content) ? '' : '-corrigida') + ".xml")
		f.setText(p.content, 'UTF-8')
		def anterior = p.version
		p.content = novo
		modelService.save(p)
		def nova = ativa()
		out << "gravado: order-process v${nova.version} ativo (anterior v${anterior}, backup ${f.path}), checkPayment presente: " + nova.content.contains("id='checkPayment'")
	}
}

if (ETAPA == 'atendente') {
	def pos = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {PointOfService} WHERE {name}='warehouse_s'")).result[0]
	if (pos == null) falha('ponto de venda warehouse_s não encontrado')
	out << 'ponto de venda warehouse_s: depósitos ' + pos.warehouses*.code
	// o OMS distribui as tarefas da remessa entre os funcionários de TODAS as lojas ligadas ao
	// depósito; o ponto de venda de exemplo Nakano (atendentes com login desligado) também estava
	// ligado ao warehouse_s e a criação da remessa falhava. Fica só o ponto de venda warehouse_s.
	def dep = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {Warehouse} WHERE {code}='warehouse_s'")).result[0]
	def outros = dep.pointsOfService.findAll { it.name != 'warehouse_s' }
	if (outros) {
		def bkp = new File('/backup/warehouse_s-pontos-de-venda-antes-' + new java.text.SimpleDateFormat('yyyyMMdd-HHmmss').format(new Date()) + '.txt')
		bkp.text = 'warehouse_s.pointsOfService antes: ' + dep.pointsOfService*.name.join(',') + '\n'
		dep.pointsOfService = dep.pointsOfService.findAll { it.name == 'warehouse_s' }
		modelService.save(dep)
		out << 'warehouse_s desligado de ' + outros*.name + ' (backup ' + bkp.path + ')'
	}
	def g = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {StoreEmployeeGroup} WHERE {uid}='cdstoreemployees'")).result[0]
	if (g == null) {
		g = modelService.create(StoreEmployeeGroupModel)
		g.uid = 'cdstoreemployees'
	}
	g.name = 'Funcionários - Centro de distribuição (loja online)'
	g.store = pos
	modelService.save(g)
	def emp = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {Employee} WHERE {uid}='atendente.cd'")).result[0]
	if (emp == null) {
		emp = modelService.create(EmployeeModel)
		emp.uid = 'atendente.cd'
	}
	emp.name = 'Atendente Centro de distribuição'
	emp.loginDisabled = false
	emp.groups = [g, userService.getUserGroupForUID('warehouseagentgroup'), userService.getUserGroupForUID('warehouseagentrole')] as Set
	modelService.save(emp)
	out << 'atendente.cd nos grupos ' + emp.groups*.uid.sort() + ' (sem senha)'
}

if (ETAPA == 'traducoes') {
	def pt = new Locale('pt')
	['PAYMENT_NOT_CAPTURED': 'Aguardando pagamento', 'PAYMENT_CAPTURED': 'Pagamento confirmado', 'READY': 'Em processamento'].each { code, nome ->
		def ev = enumerationService.getEnumerationValue('OrderStatus', code)
		def antes = enumerationService.getEnumerationName(ev, pt)
		enumerationService.setEnumerationName(ev, nome, pt)
		out << "OrderStatus.${code}: pt '${antes}' -> '${nome}'"
	}
}

return out.join('\n')
