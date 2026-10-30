// =====================================================================
//  SÓCIO-TORCEDOR (Nação Maraca) — cadastro no SAP Commerce
//  Rodar em: HAC › Console › Scripting Languages (Groovy, modo "commit")
//  Pode rodar de novo: só cria o que falta e atualiza o resto.
//  Desfazer: socio-torcedor-remover.groovy
//
//  Cria:
//   - frequência de cobrança "mensal", plano de cobrança e prazo anual
//   - categoria socio_torcedor e os planos Nação Maraca 1, 2 e 3
//     (produtos de assinatura) + convidados (R$ 60 e R$ 35/mês)
//   - plano de preço de cada um: 1ª mensalidade no ato + 11 recorrentes
//   - vagas do Maraca 1 (estoque) — esgotou, sai de venda
//   - benefícios (entitlements) de cada plano
//   - grupos nacao-maraca-1/2/3 (quem comprou o plano entra no grupo)
//   - promoções: 75%, 65% e 60% de desconto no ingresso para cada grupo
//   - passo completeOrder do order-process: pedido com plano põe o
//     cliente no grupo do plano (nova versão; a anterior fica guardada)
// =====================================================================
import de.hybris.platform.core.Registry
import de.hybris.platform.servicelayer.impex.ImportConfig
import de.hybris.platform.servicelayer.impex.impl.StreamBasedImpExResource
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

// ---------- parâmetros ----------
def CATALOGO = 'flamengoProductCatalog'
def VAGAS_MARACA_1 = 50            // limite de titulares do Maraca 1 (demo)
def GRUPO_PROMO = 'electronicsPromoGrp'
def MODULO_REGRAS = 'promotions-module'
def SETORES = ['setor-norte', 'setor-sul', 'setor-leste-inf', 'setor-oeste-sup'] // categorias das poltronas
def PLANOS = [
  [n: 1, preco: 357.90, desconto: 75, convidados: 3, convidado: 'NACAO-CONVIDADO-60', estrelas: 1, onda: 'Ondas 1 e 2',
   resumo: 'Prioridade 1 · 75% de desconto na inteira · compra garantida no Maracanã',
   beneficios: ['Prioridade 1 na compra de ingressos para jogos no Maracanã e como visitante',
                '75% de desconto sobre o valor da inteira',
                'Garantia de compra de ingressos para os jogos do Flamengo como mandante no Maracanã na sua onda de prioridade',
                'Direito de contratação de até 3 convidados por R$ 60,00/mês cada, com compra na mesma prioridade do titular',
                'Rating que valoriza tempo de permanência',
                'Direito à compra do Pacote Maracanã anual']],
  [n: 2, preco: 165.00, desconto: 65, convidados: 2, convidado: 'NACAO-CONVIDADO-60', estrelas: 2, onda: 'Ondas 3, 4 e 5',
   resumo: 'Prioridade 2 · 65% de desconto na inteira · 10% da carga reservada',
   beneficios: ['Prioridade 2 na compra de ingressos para jogos no Maracanã e como visitante',
                '65% de desconto sobre o valor da inteira',
                '10% da carga de ingressos reservada em sua onda de prioridade para jogos como mandante no Maracanã',
                'Direito de contratação de até 2 convidados por R$ 60,00/mês cada, com compra na mesma prioridade do titular',
                'Rating que valoriza frequência no Maracanã e tempo de permanência',
                'Direito à compra do Pacote Maracanã anual']],
  [n: 3, preco: 67.10, desconto: 60, convidados: 1, convidado: 'NACAO-CONVIDADO-35', estrelas: 1, onda: 'Ondas 5 e 6',
   resumo: 'Prioridade 3 · 60% de desconto na inteira · 10% da carga reservada',
   beneficios: ['Prioridade 3 na compra de ingressos para jogos no Maracanã e como visitante',
                '60% de desconto sobre o valor da inteira',
                '10% da carga de ingressos reservada em sua onda de prioridade para jogos como mandante no Maracanã',
                'Direito de contratação de 1 convidado por R$ 35,00/mês, com compra na mesma prioridade do titular',
                'Rating que valoriza tempo de permanência',
                'Direito à compra do Pacote Maracanã anual']],
]
def CONVIDADOS = [
  [code: 'NACAO-CONVIDADO-60', nome: 'Convidado Nação Maraca 1 e 2', preco: 60.00,
   resumo: 'Convidado do titular dos planos Nação Maraca 1 e 2 · compra na mesma prioridade'],
  [code: 'NACAO-CONVIDADO-35', nome: 'Convidado Nação Maraca 3', preco: 35.00,
   resumo: 'Convidado do titular do plano Nação Maraca 3 · compra na mesma prioridade'],
]
def COMUNS = [
  'nacao-frete-gratis'     : 'Frete grátis no e-commerce oficial (compras a partir de R$ 60)',
  'nacao-cashback'         : '10% de cashback nas lojas oficiais',
  'nacao-personalizacao'   : 'Personalização grátis na camisa oficial',
  'nacao-premios'          : 'Chances extras no Nação Prêmios',
  'nacao-parceiros'        : 'Benefícios de parceiros: Zé Delivery, seguro-viagem e Tô no Maraca',
  'nacao-pacote-maracana'  : 'Direito à compra do Pacote Maracanã anual',
]
// ---------------------------------

def ctx = Registry.applicationContext
def fs = ctx.getBean('flexibleSearchService')
def ms = ctx.getBean('modelService')
def cvs = ctx.getBean('catalogVersionService')
def i18n = ctx.getBean('commonI18NService')
def pt = new Locale('pt')
def online = cvs.getCatalogVersion(CATALOGO, 'Online')
def brl = i18n.getCurrency('BRL')
def log = []
def one = { String q, Map p = [:] -> def r = fs.search(new FlexibleSearchQuery(q, p)).result; r ? r[0] : null }
def money = { BigDecimal v -> v.setScale(2, BigDecimal.ROUND_HALF_UP).toString() }

// ---------- 1. ImpEx: grupos, cobrança, prazo, categoria, produtos, estoque ----------
def q = { String s -> '"' + s.replace('"', '""') + '"' }
def L = []
// groups=customergroup: a API /users/{id}/customergroups (usada pela loja para
// saber o plano do torcedor) só lista grupos que estão dentro de customergroup
L << 'INSERT_UPDATE UserGroup;uid[unique=true];locname[lang=pt];locname[lang=en];description;groups(uid)[mode=append]'
PLANOS.each { p -> L << ";nacao-maraca-${p.n};Sócio Nação Maraca ${p.n};Nação Maraca ${p.n} member;Titulares do plano Nação Maraca ${p.n} (entram ao comprar o plano);customergroup" }
L << ''
L << 'INSERT_UPDATE BillingFrequency;code[unique=true];nameInCart[lang=pt];nameInOrder[lang=pt];nameInCart[lang=en];nameInOrder[lang=en];order;cartAware'
L << ';monthly;Mensal;Mensal;Monthly;Monthly;10;false'
L << ''
L << 'INSERT_UPDATE BillingPlan;id[unique=true];name[lang=pt];name[lang=en];billingFrequency(code);billingCycleType(code)'
L << ';nacao-mensal;Cobrança mensal;Monthly billing;monthly;subscription_start'
L << ''
L << 'INSERT_UPDATE SubscriptionTerm;id[unique=true];name[lang=pt];name[lang=en];termOfServiceFrequency(code);termOfServiceNumber;termOfServiceRenewal(code);cancellable;billingPlan(id)'
L << ';nacao-anual;Contrato anual · cobrança mensal · renovação automática;Annual contract · monthly billing · auto-renewal;annually;1;auto_renewing;true;nacao-mensal'
L << ''
L << '$cv=catalogVersion(catalog(id[default=' + CATALOGO + ']),version[default=Online])[unique=true,default=' + CATALOGO + ':Online]'
L << 'INSERT_UPDATE Category;code[unique=true];$cv;name[lang=pt];name[lang=en]'
L << ';socio_torcedor;;Sócio-Torcedor;Membership'
L << ''
L << 'INSERT_UPDATE SubscriptionProduct;code[unique=true];$cv;name[lang=pt];name[lang=en];summary[lang=pt];description[lang=pt];supercategories(code,$cv);subscriptionTerm(id);unit(code)[default=pieces];approvalstatus(code)[default=approved]'
PLANOS.each { p ->
  def desc = '<ul>' + p.beneficios.collect { "<li>${it}</li>" }.join('') + '</ul>'
  L << ";NACAO-MARACA-${p.n};;${q('Nação Maraca ' + p.n)};${q('Nação Maraca ' + p.n)};${q(p.resumo)};${q(desc)};socio_torcedor;nacao-anual;;"
}
CONVIDADOS.each { c -> L << ";${c.code};;${q(c.nome)};${q(c.nome)};${q(c.resumo)};${q('<p>' + c.resumo + '</p>')};socio_torcedor;nacao-anual;;" }
L << ''
// estoque só na 1ª vez: rodar de novo não devolve as vagas já vendidas do Maraca 1
if (one("SELECT {pk} FROM {StockLevel} WHERE {productCode}='NACAO-MARACA-1'") == null) {
  L << 'INSERT_UPDATE StockLevel;productCode[unique=true];warehouse(code)[unique=true,default=warehouse_s];available;reserved[default=0];preOrder[default=0];maxPreOrder[default=0];maxStockLevelHistoryCount[default=0];inStockStatus(code)[default=notSpecified]'
  L << ";NACAO-MARACA-1;;${VAGAS_MARACA_1};;;;;"
  (['NACAO-MARACA-2', 'NACAO-MARACA-3'] + CONVIDADOS*.code).each { c -> L << ";${c};;100000;;;;;" }
}
def cfg = new ImportConfig()
cfg.script = new StreamBasedImpExResource(new ByteArrayInputStream(L.join('\n').getBytes('UTF-8')), 'UTF-8')
cfg.legacyMode = false
cfg.synchronous = true
def res = ctx.getBean('importService').importData(cfg)
if (!res.successful) {
  def det = res.hasUnresolvedLines() ? ctx.getBean('mediaService').getStreamFromMedia(res.unresolvedLines).text : ''
  throw new IllegalStateException('ImpEx falhou:\n' + det)
}
log << 'ImpEx ok: grupos, cobrança mensal, prazo anual, categoria, 5 produtos de assinatura, estoque'

// ---------- 2. planos de preço (1ª mensalidade no ato + 11 recorrentes) ----------
def monthly = one("SELECT {pk} FROM {BillingFrequency} WHERE {code}='monthly'")
def paynow = one("SELECT {pk} FROM {BillingEvent} WHERE {code}='paynow'")
def precos = PLANOS.collectEntries { ["NACAO-MARACA-${it.n}".toString(), it.preco as BigDecimal] } + CONVIDADOS.collectEntries { [it.code, it.preco as BigDecimal] }
precos.each { code, valor ->
  def prod = one('SELECT {pk} FROM {Product} WHERE {code}=?c AND {catalogVersion}=?cv', [c: code, cv: online])
  prod.europe1Prices.findAll { it.itemtype == 'SubscriptionPricePlan' }.each { pp ->
    (pp.oneTimeChargeEntries + pp.recurringChargeEntries).each { ms.remove(it) }
    ms.remove(pp)
  }
  def pp = ms.create('SubscriptionPricePlan')
  pp.product = prod
  pp.currency = brl
  pp.price = valor.doubleValue()
  pp.unit = one("SELECT {pk} FROM {Unit} WHERE {code}='pieces'")
  pp.net = false
  pp.setName("${prod.getName(pt)} · R\$ ${money(valor)}/mês".toString(), pt)
  ms.save(pp)
  def ot = ms.create('OneTimeChargeEntry')
  ot.id = code + '-adesao'
  ot.setName('1ª mensalidade', pt)
  ot.billingEvent = paynow
  ot.price = valor.doubleValue()
  ot.subscriptionPricePlanOneTime = pp
  ms.save(ot)
  def rc = ms.create('RecurringChargeEntry')
  rc.id = code + '-mensal'
  rc.cycleStart = 2
  rc.cycleEnd = 12
  rc.price = valor.doubleValue()
  // frequência e moeda vêm do prazo do produto e do plano de preço (atributos dinâmicos)
  rc.subscriptionPricePlanRecurring = pp
  ms.save(rc)
  log << "preço ${code}: R\$ ${money(valor)} no ato + 11× R\$ ${money(valor)}/mês"
}

// ---------- 3. benefícios (entitlements) ----------
def ent = { String id, String nome ->
  def e = one('SELECT {pk} FROM {Entitlement} WHERE {id}=?i', [i: id]) ?: ms.create('Entitlement')
  e.id = id
  e.setName(nome, pt)
  ms.save(e)
  e
}
def especificos = [
  'nacao-prioridade-ingresso': 'Prioridade na compra de ingressos (onda de vendas)',
  'nacao-desconto-ingresso'  : 'Desconto sobre o valor da inteira (%)',
  'nacao-garantia-compra'    : 'Garantia de compra de ingresso na sua onda',
  'nacao-reserva-carga'      : 'Carga de ingressos reservada na sua onda (%)',
  'nacao-convidados'         : 'Convidados com a mesma prioridade (máximo)',
  'nacao-estrelas'           : 'Estrelas possíveis no rating',
]
def ents = (especificos + COMUNS).collectEntries { id, nome -> [id, ent(id, nome)] }
PLANOS.each { p ->
  def prod = one('SELECT {pk} FROM {Product} WHERE {code}=?c AND {catalogVersion}=?cv', [c: "NACAO-MARACA-${p.n}".toString(), cv: online])
  fs.search(new FlexibleSearchQuery('SELECT {pk} FROM {ProductEntitlement} WHERE {subscriptionProduct}=?p', [p: prod])).result.each { ms.remove(it) }
  def pe = { String id, Integer qtd, String cond, String desc ->
    def x = ms.create('ProductEntitlement')
    x.id = "NACAO-MARACA-${p.n}-${id}".toString()
    x.entitlement = ents[id]
    x.subscriptionProduct = prod
    x.catalogVersion = online
    if (qtd != null) { x.quantity = qtd }
    if (cond) { x.conditionString = cond }
    x.setDescription(desc, pt)
    ms.save(x)
  }
  pe('nacao-prioridade-ingresso', p.n, p.onda, "Prioridade ${p.n} · ${p.onda}".toString())
  pe('nacao-desconto-ingresso', p.desconto, null, "${p.desconto}% de desconto na inteira".toString())
  if (p.n == 1) { pe('nacao-garantia-compra', null, null, 'Compra garantida na sua onda (jogos como mandante no Maracanã)') }
  else { pe('nacao-reserva-carga', 10, null, '10% da carga reservada na sua onda') }
  pe('nacao-convidados', p.convidados, p.convidado, "Até ${p.convidados} convidado(s)".toString())
  pe('nacao-estrelas', p.estrelas, null, "Até ${p.estrelas} estrela(s) no rating".toString())
  COMUNS.each { id, nome -> pe(id, null, null, nome) }
}
log << 'benefícios: ' + ents.size() + ' tipos, ligados aos 3 planos'

// ---------- 4. promoções: desconto no ingresso por plano ----------
def site = ctx.getBean('flexibleSearchService').search("SELECT {pk} FROM {PromotionGroup} WHERE {Identifier}='" + GRUPO_PROMO + "'").result[0]
def ruleGroup = one("SELECT {pk} FROM {RuleGroup} WHERE {code}='customerPromotionRuleGroup'")
def regras = []
PLANOS.each { p ->
  def code = "nacao_maraca_${p.n}_desconto_ingresso".toString()
  def r = one('SELECT {pk} FROM {PromotionSourceRule} WHERE {code}=?c', [c: code]) ?: ms.create('PromotionSourceRule')
  r.code = code
  r.setName("Nação Maraca ${p.n}: ${p.desconto}% no ingresso".toString(), pt)
  r.setName("Nação Maraca ${p.n}: ${p.desconto}% off tickets".toString(), Locale.ENGLISH)
  r.setDescription("Sócio-torcedor Nação Maraca ${p.n} paga ${100 - p.desconto}% da inteira".toString(), pt)
  r.setMessageFired("Sócio Nação Maraca ${p.n}: ${p.desconto}% de desconto no ingresso".toString(), pt)
  r.setMessageFired("Nação Maraca ${p.n} member: ${p.desconto}% off tickets".toString(), Locale.ENGLISH)
  r.priority = 100
  r.stackable = false
  r.website = site
  r.ruleGroup = ruleGroup
  r.conditions = groovy.json.JsonOutput.toJson([
    [definitionId: 'y_target_customers', parameters: [
      customer_groups_operator: [type: 'Enum(de.hybris.platform.ruledefinitions.CollectionOperator)', value: 'CONTAINS_ANY'],
      customer_groups: [type: 'List(ItemType(UserGroup))', value: ["nacao-maraca-${p.n}".toString()]],
      customers: [type: 'List(ItemType(Customer))', value: []],
      excluded_customer_groups: [type: 'List(ItemType(UserGroup))', value: []],
      excluded_customers: [type: 'List(ItemType(Customer))', value: []]], children: []],
    [definitionId: 'y_qualifying_categories', parameters: [
      quantity: [type: 'java.lang.Integer', value: 1],
      operator: [type: 'Enum(de.hybris.platform.ruledefinitions.AmountOperator)', value: 'GREATER_THAN_OR_EQUAL'],
      categories_operator: [type: 'Enum(de.hybris.platform.ruledefinitions.CollectionOperator)', value: 'CONTAINS_ANY'],
      categories: [type: 'List(ItemType(Category))', value: SETORES],
      excluded_categories: [type: 'List(ItemType(Category))', value: []],
      excluded_products: [type: 'List(ItemType(Product))', value: []]], children: []]])
  r.actions = groovy.json.JsonOutput.toJson([
    [definitionId: 'y_order_entry_percentage_discount', parameters: [
      value: [type: 'java.math.BigDecimal', value: p.desconto]]]])
  ms.save(r)
  regras << r
}
// O catálogo do Flamengo precisa estar ligado ao contexto de promoções (sem isso
// nenhuma promoção é avaliada nos carrinhos com produtos dele). Só o Online:
// ligar também o Staged ao contexto de preview deixa a avaliação ambígua.
def cvMap = one('SELECT {pk} FROM {CatalogVersionToRuleEngineContextMapping} WHERE {catalogVersion}=?cv', [cv: online])
if (!cvMap) {
  cvMap = ms.create('CatalogVersionToRuleEngineContextMapping')
  cvMap.catalogVersion = online
  cvMap.context = one("SELECT {pk} FROM {DroolsRuleEngineContext} WHERE {name}='promotions-context'")
  ms.save(cvMap)
  log << 'catálogo do Flamengo ligado ao contexto de promoções'
}
// Publicar FORA da transação do console: publicar dentro dela trava (a troca
// do módulo de regras espera linhas que a própria transação segura).
// A thread espera o commit do script e publica; o resultado vai para o arquivo.
def regrasPk = regras*.pk
def tenant = Registry.currentTenant
def saida = new File('/backup/antes-socio-torcedor-20261030/promocoes-publicacao.txt')
saida.text = 'agendado ' + new Date() + '\n'
Thread.start('socio-publica-promocoes') {
  Registry.setCurrentTenant(tenant)
  try {
    Thread.sleep(5000)
    def rs = regrasPk.collect { ms.get(it) }
    def res = ctx.getBean('ruleMaintenanceService').compileAndPublishRulesWithBlocking(rs, MODULO_REGRAS, true)
    saida << 'publicação: ' + res.result + ' ' + new Date() + '\n'
    rs.each { ms.refresh(it); it.status = de.hybris.platform.ruleengineservices.enums.RuleStatus.PUBLISHED; ms.save(it) }
  } catch (Exception e) {
    saida << 'ERRO ' + e + '\n'
  } finally {
    Registry.unsetCurrentTenant()
  }
}
log << 'promoções salvas; publicação no ' + MODULO_REGRAS + ' em segundo plano (resultado em ' + saida.path + ')'

// ---------- 5. order-process: pedido com plano põe o cliente no grupo ----------
def BLOCO = '''
// Sócio-torcedor: pedido com plano Nação Maraca põe o cliente no grupo do plano
try {
  def planos = o.entries*.product*.code.findAll { it?.startsWith('NACAO-MARACA-') }.sort()
  if (planos) {
    def grupo = ctx.getBean('userService').getUserGroupForUID('nacao-maraca-' + planos[0].substring(13))
    def cliente = o.user
    cliente.groups = ((cliente.groups ?: []).findAll { !(it.uid ?: '').startsWith('nacao-maraca-') } + [grupo]) as Set
    ms.save(cliente)
  }
} catch (Exception ex) {
  org.slf4j.LoggerFactory.getLogger('socio-torcedor').error('Falha ao tornar o cliente socio no pedido ' + o.code, ex)
}
'''
def def_ = one("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")
if (!def_.content.contains('Sócio-torcedor: pedido com plano')) {
  def marca = 'o.status = OrderStatus.COMPLETED'
  if (!def_.content.contains(marca)) { throw new IllegalStateException('completeOrder não encontrado no order-process') }
  def anterior = def_.version
  // cópia da versão atual, para voltar se precisar
  new File('/backup/antes-socio-torcedor-20261030/order-process-v' + anterior + '.xml').setText(def_.content, 'UTF-8')
  def_.content = def_.content.replace(marca, BLOCO.trim() + '\n' + marca)
  ms.save(def_)
  def ativo = one("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")
  log << "order-process: versão ${ativo.version} ativa (anterior ${anterior} guardada)"
} else {
  log << 'order-process: já atualizado'
}

// ---------- 6. busca: reindexa para os planos aparecerem com preço ----------
try {
  def cs = ctx.getBean('cronJobService')
  cs.performCronJob(cs.getCronJob('full-electronicsIndex-cronJob'), false)
  log << 'reindexação da busca disparada'
} catch (Exception e) { log << 'aviso: reindexação não disparada (' + e.message + ')' }

return log.join('\n')
