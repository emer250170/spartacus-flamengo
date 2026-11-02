/*
 * Termo do Nação versionado (item 4 do plano de evolução): dados iniciais.
 * Rodar no HAC (Console › Scripting, Groovy) DEPOIS do Update do tipo e da loja com a
 * caixa de aceite no checkout publicada (PR 4b). Antes disso, sem versão publicada,
 * nada muda na loja.
 *
 * - v2026.10: publicada em 01/10/2026 (versão atual);
 * - v2026.11: rascunho (sem "Publicado em"), para o gestor publicar na demo;
 * - aceite da v2026.10 para cada sócio atual (plano comprado), origem "Migração",
 *   na data da adesão (primeiro evento de Adesão; sem ele, a data do cadastro).
 *
 * MODO = 'conferir' só mostra o que faria; 'aplicar' grava (rodar com commit=true).
 * Pode rodar de novo: não duplica versões nem aceites.
 * Texto fictício, só para a demonstração.
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def MODO = 'conferir'

def fss = spring.getBean('flexibleSearchService')
def ms = spring.getBean('modelService')
def planService = spring.getBean('socioPlanService')
def PT = new Locale('pt')
def EN = Locale.ENGLISH
def fmt = new java.text.SimpleDateFormat('dd/MM/yyyy HH:mm')
fmt.setTimeZone(TimeZone.getTimeZone('America/Sao_Paulo'))
def out = new StringBuilder("Termo do Nação · modo ${MODO}\n")

def textoPt = [
	'Este termo regula a adesão ao programa de sócio-torcedor Nação, do Clube de Regatas do Flamengo. Texto fictício, criado para esta demonstração.',
	'Mensalidade: o plano é cobrado todo mês no cartão cadastrado. Com mensalidade em atraso, os benefícios ficam suspensos até a regularização, sem perda da vaga no plano.',
	'Ingressos: o sócio compra na onda de venda do seu plano, com o desconto do plano, até o limite por jogo (titular e convidados contratados).',
	'Convidados: cada vaga de convidado contratada recebe um convidado nominal (nome e CPF). Convidado com ingresso para jogo futuro só pode ser trocado depois do jogo.',
	'Cancelamento: o sócio pode pedir o cancelamento a qualquer momento em Meu sócio-torcedor; o plano é encerrado quando o atendimento confirma o pedido.',
	'Dados pessoais: os dados do sócio e dos convidados são usados para a gestão do programa e o acesso ao estádio, conforme a LGPD.',
]
def textoEn = [
	'These terms govern membership in the Nação supporter program of Clube de Regatas do Flamengo. Fictitious text, created for this demo.',
	'Monthly fee: the plan is charged every month to the registered card. With an overdue fee, benefits are suspended until payment, without losing the place in the plan.',
	'Tickets: members buy in their plan\'s sales wave, with the plan discount, up to the per-match limit (member and contracted guests).',
	'Guests: each contracted guest slot gets a named guest (name and CPF). A guest with a ticket for a future match can only be replaced after the match.',
	'Cancellation: members can ask to cancel at any time in My membership; the plan ends when customer service confirms the request.',
	'Personal data: member and guest data are used to manage the program and stadium access, in line with the LGPD.',
]
def biometriaPt = 'Biometria facial: a partir de 1º de janeiro de 2027, o acesso ao Maracanã exige a biometria facial validada do sócio e de cada convidado.'
def biometriaEn = 'Face biometrics: from January 1st, 2027, access to the Maracanã requires validated face biometrics for the member and each guest.'

def versao = { String code, String tituloPt, String tituloEn, List pt, List en, String resumoPt, String resumoEn, Date publicado ->
	def found = fss.search(new FlexibleSearchQuery("SELECT {pk} FROM {SocioTermVersion} WHERE {version}='${code}'")).result
	if (found) {
		out << "${code}: já existe (publicado em ${found[0].publishedAt ? fmt.format(found[0].publishedAt) : 'rascunho'})\n"
		return found[0]
	}
	out << "${code}: criar (${publicado ? 'publicado em ' + fmt.format(publicado) : 'rascunho'})\n"
	if (MODO != 'aplicar') return null
	def v = ms.create('SocioTermVersion')
	v.version = code
	v.setTitle(tituloPt, PT); v.setTitle(tituloEn, EN)
	v.setText(pt.join('\n\n'), PT); v.setText(en.join('\n\n'), EN)
	v.setSummary(resumoPt, PT); v.setSummary(resumoEn, EN)
	v.publishedAt = publicado
	ms.save(v)
	return v
}

def outubro = new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm z').parse('2026-10-01 00:00 BRT')
def v10 = versao('v2026.10', 'Termo de Adesão ao Programa Nação', 'Nação Program Membership Terms', textoPt, textoEn,
		'Primeira versão do Termo do Nação.', 'First version of the Nação terms.', outubro)
versao('v2026.11', 'Termo de Adesão ao Programa Nação', 'Nação Program Membership Terms', textoPt + [biometriaPt], textoEn + [biometriaEn],
		'Nova regra: biometria facial obrigatória para entrar no Maracanã a partir de 2027 (sócio e convidados).',
		'New rule: face biometrics required to enter the Maracanã from 2027 (member and guests).', null)

// sócios atuais (plano comprado) sem aceite da v2026.10
def socios = [:]
planService.plans.each { plan ->
	plan.customerGroup?.members?.each { m ->
		if (m instanceof de.hybris.platform.core.model.user.CustomerModel && !socios.containsKey(m.uid) && planService.memberPlan(m) != null) {
			socios[m.uid] = m
		}
	}
}
int novos = 0, jaTinham = 0
socios.values().sort { it.uid }.each { c ->
	def aceite = v10 == null ? [] : fss.search(new FlexibleSearchQuery('SELECT {pk} FROM {SocioTermAcceptance} WHERE {customer}=?c AND {termVersion}=?v', [c: c, v: v10])).result
	if (aceite) { jaTinham++; return }
	def adesao = fss.search(new FlexibleSearchQuery("SELECT {e.pk} FROM {SocioEvent AS e JOIN SocioEventType AS t ON {e.type}={t.pk}} WHERE {e.customer}=?c AND {t.code}='ADESAO' ORDER BY {e.date} ASC", [c: c])).result
	def quando = adesao ? (adesao[0].date ?: adesao[0].creationtime) : c.creationtime
	out << "  aceite v2026.10 para ${c.uid} (${planService.memberPlan(c).code}, ${fmt.format(quando)})\n"
	novos++
	if (MODO == 'aplicar' && v10 != null) {
		def a = ms.create('SocioTermAcceptance')
		a.customer = c
		a.termVersion = v10
		a.date = quando
		a.origin = 'Migração'
		a.plan = planService.memberPlan(c)
		ms.save(a)
	}
}
out << "Sócios atuais: ${socios.size()} · aceites novos: ${novos} · já tinham: ${jaTinham}\n"
out << (MODO == 'aplicar' ? 'Gravado (confira que rodou com commit=true).' : 'Nada gravado (modo conferir).')
return out.toString()
