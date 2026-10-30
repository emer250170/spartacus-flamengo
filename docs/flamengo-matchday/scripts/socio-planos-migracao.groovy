// =====================================================================
//  SÓCIO-TORCEDOR — planos no cadastro do Backoffice (perspectiva Sócio-Torcedor)
//  Rodar em: HAC › Console › Scripting Languages (Groovy, modo "commit"),
//  DEPOIS de: ant all + HAC › Platform › Update (Update running system +
//  Localize types) + restart. Pode rodar de novo: só cria o que falta.
//
//  1. papel "Gestor do sócio-torcedor" (sociomanagerrole) e perspectiva
//     socioperspective também para o admin
//  2. usuário gestor.socio SEM senha (a senha é definida no Backoffice)
//  3. planos Nação Maraca 1, 2 e 3 no cadastro (SocioPlan), ligados aos
//     produtos, grupos e promoções que já existem; ao salvar, a descrição do
//     produto ganha as regras que a loja passa a ler (onda, desconto, convidados)
//  4. planos Nação Sem Fronteiras Anual e Mensal, fora da loja (base simulada
//     para os relatórios; o gestor pode colocá-los à venda no Backoffice)
//  5. order-process: pedido com plano chama socioPlanService.applyOrder
//     (vale para qualquer plano novo); a versão anterior fica guardada
//
//  Backup antes de mudar: /backup/antes-socio-planos-<data>/
// =====================================================================
import de.hybris.platform.core.Registry
import de.hybris.platform.servicelayer.impex.ImportConfig
import de.hybris.platform.servicelayer.impex.impl.StreamBasedImpExResource
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def ctx = Registry.applicationContext
def fs = ctx.getBean('flexibleSearchService')
def ms = ctx.getBean('modelService')
def us = ctx.getBean('userService')
def online = ctx.getBean('catalogVersionService').getCatalogVersion('flamengoProductCatalog', 'Online')
def pt = new Locale('pt')
def en = Locale.ENGLISH
def log = []
def one = { String q, Map p = [:] -> def r = fs.search(new FlexibleSearchQuery(q, p)).result; r ? r[0] : null }
def product = { String code -> one('SELECT {pk} FROM {Product} WHERE {code}=?c AND {catalogVersion}=?cv', [c: code, cv: online]) }
def bk = new File('/backup/antes-socio-planos-' + new Date().format('yyyyMMdd-HHmm'))
bk.mkdirs()

def impex = { List<String> lines ->
  def cfg = new ImportConfig()
  cfg.script = new StreamBasedImpExResource(new ByteArrayInputStream(lines.join('\n').getBytes('UTF-8')), 'UTF-8')
  cfg.legacyMode = false
  cfg.synchronous = true
  def res = ctx.getBean('importService').importData(cfg)
  if (!res.successful) {
    def det = res.hasUnresolvedLines() ? ctx.getBean('mediaService').getStreamFromMedia(res.unresolvedLines).text : ''
    throw new IllegalStateException('ImpEx falhou:\n' + det)
  }
}

// ---------- 1. papel ----------
def roleFile = new File('/opt/sap/hybris/bin/custom/flamengomatchday/resources/impex/essentialdata-flamengomatchday-socio-role.impex')
impex(roleFile.getText('UTF-8').readLines())
log << 'papel sociomanagerrole + perspectiva socioperspective (admin também)'

// ---------- 2. usuário sem senha ----------
if (one("SELECT {pk} FROM {Employee} WHERE {uid}='gestor.socio'") == null) {
  impex(['INSERT_UPDATE Employee;uid[unique=true];name;groups(uid);sessionLanguage(isocode);backOfficeLoginDisabled',
         ';gestor.socio;Gestor do sócio-torcedor (demo);sociomanagerrole;pt;false'])
  log << 'usuário gestor.socio criado SEM senha: defina a senha no Backoffice (Usuário › Funcionários)'
} else {
  log << 'usuário gestor.socio já existe'
}

// ---------- 3. Nação Maraca 1, 2 e 3 ----------
def items = { String html ->
  def clean = (html ?: '').replaceAll(/(?s)<!--socio-plan.*?-->/, '')
  def li = (clean =~ /(?s)<li[^>]*>(.*?)<\/li>/).collect { it[1].replaceAll(/<[^>]+>/, '').replace('&amp;', '&').trim() }.findAll { it }
  li.join('\n')
}
def MARACA = [
  [n: 1, preco: 357.90d, desconto: 75, convidados: 3, convidado: 60.00d, vagas: 50, base: 10689],
  [n: 2, preco: 165.00d, desconto: 65, convidados: 2, convidado: 60.00d, vagas: null, base: 13489],
  [n: 3, preco: 67.10d, desconto: 60, convidados: 1, convidado: 35.00d, vagas: null, base: 18075],
]
MARACA.each { m ->
  def code = "NACAO-MARACA-${m.n}".toString()
  def prod = product(code)
  if (prod == null) { log << "AVISO: produto ${code} não existe (rode socio-torcedor-cadastro.groovy)"; return }
  new File(bk, code + '-descricao-pt.html').setText(prod.getDescription(pt) ?: '', 'UTF-8')
  new File(bk, code + '-descricao-en.html').setText(prod.getDescription(en) ?: '', 'UTF-8')
  def plan = one('SELECT {pk} FROM {SocioPlan} WHERE {code}=?c', [c: code])
  if (plan != null) { log << "${code}: já está no cadastro (${plan.status})"; return }
  plan = ms.create('SocioPlan')
  plan.code = code
  plan.setName("Nação Maraca ${m.n}".toString(), pt)
  plan.setName("Nação Maraca ${m.n}".toString(), en)
  plan.family = 'Nação Maraca'
  plan.setSummary(prod.getSummary(pt), pt)
  if (prod.getSummary(en)) { plan.setSummary(prod.getSummary(en), en) }
  plan.setBenefits(items(prod.getDescription(pt)), pt)
  if (prod.getDescription(en)) { plan.setBenefits(items(prod.getDescription(en)), en) }
  plan.monthlyPrice = m.preco
  plan.ticketDiscount = m.desconto
  plan.wave = m.n
  plan.maxGuests = m.convidados
  plan.guestPrice = m.convidado
  plan.slots = m.vagas
  plan.sortOrder = m.n
  plan.product = prod
  plan.customerGroup = us.getUserGroupForUID("nacao-maraca-${m.n}".toString())
  plan.pendingGroup = us.getUserGroupForUID("nacao-pendente-${m.n}".toString())
  plan.promotionCode = "nacao_maraca_${m.n}_desconto_ingresso".toString()
  plan.demoBase = m.base
  plan.status = br.com.flamengo.matchday.enums.SocioPlanStatus.ON_SALE
  ms.save(plan)   // o interceptor publica: descrição com as regras, preço, vagas, benefícios
  log << "${code}: no cadastro e publicado — ${plan.statusMessage}"
}

// ---------- 4. Nação Sem Fronteiras (fora da loja) ----------
def COMUNS = ['Frete grátis no e-commerce oficial (compras a partir de R$ 60)', '10% de cashback nas lojas oficiais',
              'Personalização grátis na camisa oficial', 'Chances extras no Nação Prêmios',
              'Benefícios de parceiros: Zé Delivery, seguro-viagem e Tô no Maraca']
def COMUNS_EN = ['Free shipping on the official online store (orders from R$ 60)', '10% cashback at the official stores',
                 'Free customization of the official jersey', 'Extra chances in Nação Prêmios',
                 'Partner benefits: Zé Delivery, travel insurance and Tô no Maraca']
def SEM_FRONTEIRAS = [
  [nome: 'Nação Sem Fronteiras Anual', nomeEn: 'Nação Sem Fronteiras Annual', preco: 19.90d, base: 10037, ordem: 10,
   resumo: 'Para quem torce de longe · pagamento anual com desconto', resumoEn: 'For fans who live far away · yearly plan with discount'],
  [nome: 'Nação Sem Fronteiras Mensal', nomeEn: 'Nação Sem Fronteiras Monthly', preco: 24.90d, base: 14442, ordem: 11,
   resumo: 'Para quem torce de longe · sem fidelidade', resumoEn: 'For fans who live far away · no minimum term'],
]
SEM_FRONTEIRAS.each { m ->
  def existing = fs.search(new FlexibleSearchQuery('SELECT {pk} FROM {SocioPlan}')).result.find { it.getName(pt) == m.nome }
  if (existing != null) { log << "${m.nome}: já está no cadastro (${existing.status})"; return }
  def plan = ms.create('SocioPlan')
  plan.setName(m.nome, pt)
  plan.setName(m.nomeEn, en)
  plan.family = 'Nação Sem Fronteiras'
  plan.setSummary(m.resumo, pt)
  plan.setSummary(m.resumoEn, en)
  plan.setBenefits((['Carteirinha digital e conteúdo exclusivo do clube'] + COMUNS).join('\n'), pt)
  plan.setBenefits((['Digital member card and exclusive club content'] + COMUNS_EN).join('\n'), en)
  plan.monthlyPrice = m.preco
  plan.ticketDiscount = 0
  plan.wave = 0
  plan.maxGuests = 0
  plan.guestPrice = 0d
  plan.sortOrder = m.ordem
  plan.demoBase = m.base
  plan.status = br.com.flamengo.matchday.enums.SocioPlanStatus.OFF_SALE
  ms.save(plan)
  log << "${plan.code}: no cadastro, fora da loja (base simulada ${m.base})"
}

// ---------- 5. order-process ----------
def ANTIGO_INICIO = '// Sócio-torcedor: pedido com plano Nação Maraca põe o cliente no grupo do plano'
def NOVO = '''// Sócio-torcedor: pedido com plano põe o cliente no grupo do plano (cadastro de planos do Backoffice)
try {
  ctx.getBean('socioPlanService').applyOrder(o)
} catch (Exception ex) {
  org.slf4j.LoggerFactory.getLogger('socio-torcedor').error('Falha ao tornar o cliente socio no pedido ' + o.code, ex)
}
'''
def proc = one("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")
if (proc.content.contains('socioPlanService')) {
  log << "order-process: já usa o socioPlanService (versão ${proc.version})"
} else {
  def i = proc.content.indexOf(ANTIGO_INICIO)
  def fim = 'Falha ao tornar o cliente socio no pedido \' + o.code, ex)\n}\n'
  def j = i < 0 ? -1 : proc.content.indexOf(fim, i)
  if (i < 0 || j < 0) { throw new IllegalStateException('Bloco do sócio-torcedor não encontrado no order-process') }
  new File(bk, 'order-process-v' + proc.version + '.xml').setText(proc.content, 'UTF-8')
  def anterior = proc.version
  proc.content = proc.content.substring(0, i) + NOVO + proc.content.substring(j + fim.length())
  ms.save(proc)
  def ativo = one("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")
  log << "order-process: versão ${ativo.version} ativa (anterior ${anterior} em ${bk})"
}

log << 'backup em ' + bk
return log.join('\n')
