/*
 * Regras de saída do sócio (item 5 do plano de evolução): texto da v2026.11 do
 * Termo do Nação. Rodar no HAC (Console › Scripting, Groovy) depois do PR 5a instalado.
 *
 * Troca o parágrafo "Cancelamento" da v2026.11 (pt e en) pelo que traz o prazo de
 * arrependimento e a multa, e o "O que mudou" passa a citar biometria e regras de
 * saída. Só mexe na v2026.11 enquanto ela não estiver publicada (texto publicado,
 * com aceites, não é alterado). A v2026.10 fica como está.
 *
 * MODO = 'conferir' só mostra o que faria; 'aplicar' grava (rodar com commit=true).
 * Pode rodar de novo: se o texto já está certo, não muda nada.
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def MODO = 'conferir'

def fss = spring.getBean('flexibleSearchService')
def ms = spring.getBean('modelService')
def PT = new Locale('pt')
def EN = Locale.ENGLISH
def out = new StringBuilder("Termo do Nação v2026.11 · regras de saída · modo ${MODO}\n")

def cancelamentoPt = 'Cancelamento: o sócio pode pedir o cancelamento a qualquer momento em Meu sócio-torcedor; o plano é encerrado quando o atendimento confirma o pedido. Até 7 dias corridos depois da adesão (direito de arrependimento), o valor pago na adesão é devolvido integralmente. Depois desse prazo, é cobrada multa de 10% sobre as mensalidades restantes do contrato de 12 meses (plano e convidados). Vale a data do pedido de cancelamento.'
def cancelamentoEn = 'Cancellation: members can ask to cancel at any time in My membership; the plan ends when customer service confirms the request. Within 7 calendar days of joining (cooling-off period), the amount paid at sign-up is refunded in full. After that, a fee of 10% of the remaining monthly fees of the 12-month contract (plan and guests) applies. The date of the cancellation request counts.'
def resumoPt = 'Novas regras: biometria facial obrigatória no Maracanã a partir de 2027 (sócio e convidados) e regras de saída (arrependimento em 7 dias com reembolso; depois, multa de 10% das mensalidades restantes).'
def resumoEn = 'New rules: face biometrics required at the Maracanã from 2027 (member and guests) and exit rules (7-day cooling-off with refund; after that, a 10% fee on the remaining monthly fees).'

def found = fss.search(new FlexibleSearchQuery("SELECT {pk} FROM {SocioTermVersion} WHERE {version}='v2026.11'")).result
if (!found) {
	return out << 'v2026.11 não encontrada: rode antes o socio-termo-migracao.groovy.'
}
def v = found[0]
if (v.publishedAt != null && !v.publishedAt.after(new Date())) {
	return out << "v2026.11 já está publicada (${v.publishedAt}): o texto publicado não é alterado. Volte a versão para rascunho no Backoffice e rode de novo."
}

def troca = { String texto, String prefixo, String novo ->
	def paragrafos = (texto ?: '').split(/\n\s*\n/) as List
	def i = paragrafos.findIndexOf { it.trim().startsWith(prefixo) }
	if (i < 0) {
		// sem parágrafo de cancelamento: entra antes do último (biometria)
		paragrafos.add(Math.max(0, paragrafos.size() - 1), novo)
	} else {
		paragrafos[i] = novo
	}
	return paragrafos.join('\n\n')
}

def novoPt = troca(v.getText(PT), 'Cancelamento:', cancelamentoPt)
def novoEn = troca(v.getText(EN), 'Cancellation:', cancelamentoEn)
def mudancas = []
if (novoPt != v.getText(PT)) mudancas << 'texto pt'
if (novoEn != v.getText(EN)) mudancas << 'texto en'
if (resumoPt != v.getSummary(PT)) mudancas << 'O que mudou (pt)'
if (resumoEn != v.getSummary(EN)) mudancas << 'O que mudou (en)'

if (!mudancas) {
	return out << 'v2026.11 já está com as regras de saída. Nada a fazer.'
}
out << "Muda: ${mudancas.join(', ')}\n"
out << "Parágrafo novo (pt): ${cancelamentoPt}\n"
out << "O que mudou (pt): ${resumoPt}\n"
if (MODO == 'aplicar') {
	v.setText(novoPt, PT); v.setText(novoEn, EN)
	v.setSummary(resumoPt, PT); v.setSummary(resumoEn, EN)
	ms.save(v)
	out << 'Gravado.\n'
} else {
	out << "Nada gravado (MODO = 'conferir').\n"
}
return out
