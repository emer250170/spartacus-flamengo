/*
 * Item 12 do plano de evolução: lojas físicas do Flamengo para retirada (BOPIS).
 * Rodar no HAC (Groovy). Primeiro com commit=false (mostra o que muda), depois commit=true.
 *
 * ETAPA = 'lojas'   : backup em /backup/lojas-retirada-antes-<data>.txt, renomeia Barra e
 *                     Copacabana, cria Maracanã (endereço, horário, depósito), grupos de
 *                     funcionários das lojas, atendentes (sem senha: o Emerson define no
 *                     Backoffice), nome do modo "Retirada na loja" e tira da loja online os
 *                     pontos de venda de exemplo (Tóquio, Reino Unido...), que apareciam na
 *                     busca de lojas com o estoque do warehouse_s.
 * ETAPA = 'estoque' : estoque de retirada (QTD por SKU) nas 3 lojas, só para produtos
 *                     físicos (os que têm estoque no warehouse_s, menos ingresso, plano,
 *                     experiência, estacionamento, comida e personalização).
 *
 * Para voltar: o backup lista o que existia; as lojas/estoques novos podem ser apagados
 * pelo código (maracanastore, *storeemployees, atendente.*) e os pontos de venda de
 * exemplo voltam com baseStore=electronics (lista no backup).
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.servicelayer.impex.ImportConfig
import de.hybris.platform.servicelayer.impex.impl.StreamBasedImpExResource
import de.hybris.platform.storelocator.model.PointOfServiceModel
import de.hybris.platform.commerceservices.model.user.StoreEmployeeGroupModel
import de.hybris.platform.core.model.user.AddressModel
import de.hybris.platform.core.model.user.EmployeeModel
import de.hybris.platform.ordersplitting.model.WarehouseModel

def ETAPA = 'lojas'
def QTD = 10

def out = []
def fs = flexibleSearchService
def one = { String q, Map p = [:] -> def r = fs.search(new FlexibleSearchQuery(q, p)).result; r ? r[0] : null }
def store = baseStoreService.getBaseStoreForUid('electronics')
def PT = new Locale('pt')
def EN = Locale.ENGLISH

def LOJAS = [
	[code: 'barrastore', nome: 'Loja Flamengo Barra',
	 desc: 'Loja oficial do Flamengo na Barra da Tijuca. Retire seu pedido da loja online no balcão.'],
	[code: 'copacabanastore', nome: 'Loja Flamengo Copacabana',
	 desc: 'Loja oficial do Flamengo em Copacabana. Retire seu pedido da loja online no balcão.'],
	[code: 'maracanastore', nome: 'Loja Flamengo Maracanã',
	 desc: 'Loja oficial do Flamengo no Maracanã (Portão 18). Retire seu pedido da loja online antes do jogo.',
	 rua: 'Avenida Presidente Castelo Branco', numero: 's/n - Portão 18', bairro: 'Maracanã', cep: '20271-130',
	 lat: -22.9122d, lng: -43.2302d]
]

if (ETAPA == 'lojas') {
	// ---------------------------------------------------------------- backup
	def bk = new StringBuilder()
	def ts = new java.text.SimpleDateFormat('yyyyMMdd-HHmmss').format(new Date())
	LOJAS.each { l ->
		def p = one("SELECT {pk} FROM {PointOfService} WHERE {name}=?n", [n: l.code])
		bk << "POS ${l.code}: " << (p == null ? 'nao existia' : "displayName=${p.displayName} description=${p.description}") << '\n'
	}
	def others = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {PointOfService} WHERE {baseStore}=?s", [s: store])).result
			.findAll { !(it.name in LOJAS*.code) }
	bk << 'POS de exemplo com baseStore=electronics: ' << others*.name.join(',') << '\n'
	def pk = one("SELECT {pk} FROM {PickUpDeliveryMode} WHERE {code}='pickup'")
	bk << "pickup name pt=${pk?.getName(PT)} en=${pk?.getName(EN)}\n"
	def dir = new File('/backup'); dir.mkdirs()
	def f = new File(dir, "lojas-retirada-antes-${ts}.txt"); f.text = bk.toString()
	out << 'Backup: ' + f.path

	// ---------------------------------------------------------------- lojas
	def barra = one("SELECT {pk} FROM {PointOfService} WHERE {name}='barrastore'")
	def wBarra = barra.warehouses.find { it.code == 'barrastore' }
	LOJAS.each { l ->
		def p = one("SELECT {pk} FROM {PointOfService} WHERE {name}=?n", [n: l.code])
		if (p == null) {
			p = modelService.create(PointOfServiceModel)
			p.name = l.code
			p.type = barra.type
			p.baseStore = store
			p.openingSchedule = barra.openingSchedule
			p.features = barra.features
			def a = modelService.create(AddressModel)
			a.owner = p
			a.streetname = l.rua; a.streetnumber = l.numero; a.line1 = l.rua + ', ' + l.numero; a.line2 = l.bairro
			a.district = l.bairro; a.town = 'Rio de Janeiro'; a.postalcode = l.cep; a.country = barra.address.country
			p.address = a
			p.latitude = l.lat; p.longitude = l.lng; p.geocodeTimestamp = new Date()
			modelService.saveAll([p, a])
			def w = modelService.create(WarehouseModel)
			w.code = l.code; w.vendor = wBarra.vendor; w.default = false; w.external = false
			w.baseStores = [store]; w.deliveryModes = wBarra.deliveryModes; w.pointsOfService = [p]
			modelService.save(w)
			out << 'Criada: ' + l.code
		}
		p.displayName = l.nome
		p.description = l.desc
		modelService.save(p)
		def w = p.warehouses.find { it.code == l.code }
		if (w != null) { w.setName(l.nome, PT); w.setName(l.nome, EN); modelService.save(w) }
		// grupo de funcionários da loja (o cockpit Expedição mostra só as remessas da loja do atendente)
		def gid = l.code.replace('store', '') + 'storeemployees'
		def g = one("SELECT {pk} FROM {StoreEmployeeGroup} WHERE {uid}=?u", [u: gid])
		if (g == null) {
			g = modelService.create(StoreEmployeeGroupModel)
			g.uid = gid
		}
		g.name = 'Funcionários - ' + l.nome
		g.store = p
		modelService.save(g)
		// atendente (sem senha)
		def uid = 'atendente.' + l.code.replace('store', '')
		def e = one("SELECT {pk} FROM {Employee} WHERE {uid}=?u", [u: uid])
		if (e == null) {
			e = modelService.create(EmployeeModel)
			e.uid = uid
		}
		e.name = 'Atendente ' + l.nome
		e.loginDisabled = false
		e.groups = [g, userService.getUserGroupForUID('warehouseagentgroup'), userService.getUserGroupForUID('warehouseagentrole')] as Set
		modelService.save(e)
		out << "${l.code}: ${p.displayName} · depósito ${w?.code} · grupo ${g.uid} · atendente ${e.uid}"
	}

	// ---------------------------------------------------------------- modo de entrega
	if (pk != null) {
		pk.setName('Retirada na loja', PT); pk.setName('Pick up in store', EN)
		pk.setDescription('Retire grátis numa Loja Flamengo', PT); pk.setDescription('Free pickup at a Flamengo store', EN)
		modelService.save(pk)
		out << 'Modo pickup: ' + pk.getName(PT)
	}

	// ---------------------------------------------------------------- pontos de venda de exemplo fora da loja online
	others.each { it.baseStore = null }
	modelService.saveAll(others)
	out << 'Pontos de venda de exemplo tirados da loja online: ' + others.size()
}

if (ETAPA == 'estoque') {
	def q = new FlexibleSearchQuery("SELECT DISTINCT {s.productCode} FROM {StockLevel AS s JOIN Warehouse AS w ON {s.warehouse}={w.pk}} WHERE {w.code}='warehouse_s'")
	q.resultClassList = [String]
	def codes = fs.search(q).result.findAll { c -> !(c ==~ /(JOGO-|EXP-|NACAO-|FLA-EST-|FLA-FOOD-|FLA-PERSONALIZACAO).*/) }
	def imp = new StringBuilder('INSERT_UPDATE StockLevel;productCode[unique=true];warehouse(code)[unique=true];available;reserved;inStockStatus(code)\n')
	LOJAS.each { l -> codes.each { c -> imp << ';' << c << ';' << l.code << ';' << QTD << ';0;notSpecified\n' } }
	def cfg = new ImportConfig()
	cfg.script = new StreamBasedImpExResource(new ByteArrayInputStream(imp.toString().getBytes('UTF-8')), 'UTF-8')
	cfg.synchronous = true
	cfg.legacyMode = false
	def res = importService.importData(cfg)
	out << "Estoque: ${codes.size()} SKUs x ${LOJAS.size()} lojas, ${QTD} cada · importação " + (res.successful ? 'OK' : 'ERRO')
	LOJAS.each { l ->
		def n = fs.search(new FlexibleSearchQuery("SELECT {s.pk} FROM {StockLevel AS s JOIN Warehouse AS w ON {s.warehouse}={w.pk}} WHERE {w.code}=?c", [c: l.code])).totalCount
		out << "  ${l.code}: ${n} linhas"
	}
}

return out.join('\n')
