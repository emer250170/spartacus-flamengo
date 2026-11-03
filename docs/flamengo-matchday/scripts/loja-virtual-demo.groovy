/*
 * Item 13 do plano de evolução (ato 7 do roteiro): dados de demonstração da loja virtual.
 * Rodar no HAC (Groovy). Primeiro com commit=false (mostra o que muda), depois commit=true.
 *
 * Tudo com prefixo demo_ / FLADEMO, no grupo de promoções da loja (electronicsPromoGrp):
 *  - 7.1 demo_calcados_15: 15% em Calçados (rascunho: o apresentador publica ao vivo no cockpit
 *    Merchandising Flamengo, ou cria igual a partir do modelo; despublicar depois da demo)
 *  - 7.2 cupom FLADEMO20 (código único, 1 uso por torcedor, 100 usos no total, até 31/12/2026)
 *    + regra demo_cupom_flademo20 (R$ 20 de desconto acima de R$ 150) PUBLICADA: só vale com o
 *    cupom, então não muda nada nos outros atos
 *  - 7.3 demo_frete_gratis_299 (frete grátis acima de R$ 299: troca a entrega pelo modo
 *    frete-gratis, R$ 0, que não aparece para escolha) e demo_brinde_manto (Manto no carrinho =
 *    chaveiro de brinde): rascunhos, para publicar ao vivo e despublicar depois
 *
 * Os atos 1 a 6 não mudam: as regras em rascunho não valem na loja e a do cupom só vale com o
 * código. Para limpar: apagar as regras demo_*, o cupom FLADEMO20 e o modo frete-gratis.
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.deliveryzone.model.ZoneDeliveryModeModel
import de.hybris.platform.deliveryzone.model.ZoneDeliveryModeValueModel
import de.hybris.platform.couponservices.model.SingleCodeCouponModel
import de.hybris.platform.promotionengineservices.model.PromotionSourceRuleModel

def PUBLICAR = true   // publica só a regra do cupom (as outras ficam em rascunho)

def out = []
def fs = flexibleSearchService
def one = { String q, Map p = [:] -> def r = fs.search(new FlexibleSearchQuery(q, p)).result; r ? r[0] : null }
def PT = new Locale('pt')
def EN = Locale.ENGLISH
def website = one("SELECT {pk} FROM {PromotionGroup} WHERE {identifier}='electronicsPromoGrp'")
def grupoLoja = one("SELECT {pk} FROM {RuleGroup} WHERE {code}='flamengoLojaRuleGroup'")
def grupoCupom = one("SELECT {pk} FROM {RuleGroup} WHERE {code}='couponPromotionRuleGroup'")

// ---------------------------------------------------------------- modo de entrega do frete grátis
def zona = one("SELECT {pk} FROM {Zone} WHERE {code}='brasil'")
def brl = commonI18NService.getCurrency('BRL')
def fg = one("SELECT {pk} FROM {ZoneDeliveryMode} WHERE {code}='frete-gratis'")
if (fg == null) {
	fg = modelService.create(ZoneDeliveryModeModel)
	fg.code = 'frete-gratis'
	fg.net = false
	fg.active = true
	fg.setName('Frete grátis', PT); fg.setName('Free shipping', EN)
	fg.setDescription('Frete grátis da promoção', PT); fg.setDescription('Free shipping promotion', EN)
	modelService.save(fg)
	def v = modelService.create(ZoneDeliveryModeValueModel)
	v.deliveryMode = fg; v.zone = zona; v.currency = brl; v.value = 0d; v.minimum = 0d
	modelService.save(v)
	out << 'Modo frete-gratis criado (R$ 0, fora da lista de escolha da loja)'
}

// ---------------------------------------------------------------- cupom
def cupom = one("SELECT {pk} FROM {SingleCodeCoupon} WHERE {couponId}='FLADEMO20'")
if (cupom == null) {
	cupom = modelService.create(SingleCodeCouponModel)
	cupom.couponId = 'FLADEMO20'
}
cupom.setName('Cupom de demonstração FLADEMO20', PT)
cupom.active = true
cupom.maxRedemptionsPerCustomer = 1
cupom.maxTotalRedemptions = 100
cupom.startDate = new Date()
cupom.endDate = new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm').parse('2026-12-31 23:59')
modelService.save(cupom)
out << 'Cupom FLADEMO20 ativo (1 por torcedor, 100 no total, até 31/12/2026)'

// ---------------------------------------------------------------- regras
def cat = { List c -> '{"type":"List(ItemType(Category))","value":[' + c.collect { '"' + it + '"' }.join(',') + ']}' }
def emptyList = { String t -> '{"type":"List(ItemType(' + t + '))","value":[]}' }
def qualCats = { List c -> '{"definitionId":"y_qualifying_categories","parameters":{"quantity":{"type":"java.lang.Integer","value":1},' +
		'"operator":{"type":"Enum(de.hybris.platform.ruledefinitions.AmountOperator)","value":"GREATER_THAN_OR_EQUAL"},' +
		'"categories_operator":{"type":"Enum(de.hybris.platform.ruledefinitions.CollectionOperator)","value":"CONTAINS_ANY"},' +
		'"categories":' + cat(c) + ',"excluded_categories":' + emptyList('Category') + ',"excluded_products":' + emptyList('Product') + '},"children":[]}' }
def total = { int v -> '{"definitionId":"y_cart_total","parameters":{"value":{"type":"Map(ItemType(Currency),java.math.BigDecimal)","value":{"BRL":' + v + '}},' +
		'"operator":{"type":"Enum(de.hybris.platform.ruledefinitions.AmountOperator)","value":"GREATER_THAN_OR_EQUAL"}},"children":[]}' }

def REGRAS = [
	[code: 'demo_calcados_15', prio: 210, grupo: grupoLoja, publicar: false,
	 nome: 'Demo: 15% em Calçados', msg: '15% de desconto em Calçados',
	 cond: '[' + qualCats(['flamengo_c132']) + ']',
	 act: '[{"definitionId":"y_order_entry_percentage_discount","parameters":{"value":{"type":"java.math.BigDecimal","value":15}}}]'],
	[code: 'demo_cupom_flademo20', prio: 160, grupo: grupoCupom, publicar: PUBLICAR,
	 nome: 'Demo: cupom FLADEMO20 - R$ 20 acima de R$ 150', msg: 'Cupom FLADEMO20: R$ 20 de desconto',
	 cond: '[{"definitionId":"y_qualifying_coupons","parameters":{"coupons":{"type":"List(ItemType(AbstractCoupon))","value":["FLADEMO20"]}},"children":[]},' + total(150) + ']',
	 act: '[{"definitionId":"y_order_fixed_discount","parameters":{"value":{"type":"Map(ItemType(Currency),java.math.BigDecimal)","value":{"BRL":20}}}}]'],
	[code: 'demo_frete_gratis_299', prio: 120, grupo: grupoLoja, publicar: false,
	 nome: 'Demo: frete grátis acima de R$ 299', msg: 'Frete grátis nas compras acima de R$ 299',
	 cond: '[' + total(299) + ']',
	 act: '[{"definitionId":"y_change_delivery_mode","parameters":{"delivery_mode":{"type":"ItemType(DeliveryMode)","value":"frete-gratis"}}}]'],
	[code: 'demo_brinde_manto', prio: 115, grupo: grupoLoja, publicar: false,
	 nome: 'Demo: Manto + chaveiro de brinde', msg: 'Comprou um Manto, ganhou um chaveiro',
	 cond: '[' + qualCats(['flamengo_c115']) + ']',
	 act: '[{"definitionId":"y_free_gift","parameters":{"product":{"type":"ItemType(Product)","value":"flamengo_3911"},"quantity":{"type":"java.lang.Integer","value":1},' +
			 '"catalog":{"type":"ItemType(Catalog)","value":"flamengoProductCatalog"}}}]']
]

def publicar = []
REGRAS.each { r ->
	def m = one("SELECT {pk} FROM {PromotionSourceRule} WHERE {code}=?c", [c: r.code])
	if (m == null) {
		m = modelService.create(PromotionSourceRuleModel)
		m.code = r.code
	}
	m.website = website
	m.ruleGroup = r.grupo
	m.priority = r.prio
	m.stackable = true
	m.maxAllowedRuns = 1
	m.setName(r.nome, PT); m.setName(r.nome, EN)
	m.setMessageFired(r.msg, PT)
	m.conditions = r.cond
	m.actions = r.act
	modelService.save(m)
	if (r.publicar) { publicar << m }
	out << "${r.code}: ${r.nome} (" + (r.publicar ? 'publicar' : 'rascunho') + ')'
}
if (publicar) {
	// Publicar FORA da transação do console (dentro dela a troca do módulo de regras trava):
	// a thread espera o commit do script, publica e grava o resultado em arquivo.
	def pks = publicar*.pk
	def tenant = de.hybris.platform.core.Registry.currentTenant
	def ctx = spring
	def saida = new File('/backup/loja-virtual-demo-publicacao.txt')
	saida.text = 'agendado ' + new Date() + '\n'
	Thread.start('loja-virtual-demo-publica') {
		de.hybris.platform.core.Registry.setCurrentTenant(tenant)
		try {
			Thread.sleep(5000)
			def ms = ctx.getBean('modelService')
			def rs = pks.collect { ms.get(it) }
			def res = ctx.getBean('ruleMaintenanceService').compileAndPublishRulesWithBlocking(rs, 'promotions-module', true)
			saida << 'publicação: ' + res.result + ' ' + new Date() + '\n'
			rs.each { ms.refresh(it); it.status = de.hybris.platform.ruleengineservices.enums.RuleStatus.PUBLISHED; ms.save(it) }
		} catch (Exception e) {
			saida << 'ERRO ' + e + '\n'
		} finally {
			de.hybris.platform.core.Registry.unsetCurrentTenant()
		}
	}
	out << 'Publicação de ' + publicar*.code + ' em segundo plano (resultado em ' + saida.path + ')'
}
return out.join('\n')
