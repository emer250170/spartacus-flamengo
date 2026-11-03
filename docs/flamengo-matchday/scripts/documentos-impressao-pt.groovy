/*
 * Item 14 do plano de evolução: documentos de impressão da remessa com o Backoffice em português.
 *
 * Sintoma: no cockpit Atendimento do pedido › Consignações, os botões "Documento de retirada",
 * "Etiqueta de embarque", "Formulário de exportação" e os de devolução davam "Internal server error"
 * (log: RendererException: No content found for template electronics-spa-CMSImageComponent-template).
 *
 * Causa: os modelos (RendererTemplate) desses documentos e dos componentes do site electronics-spa
 * só têm conteúdo em inglês. A geração do documento usa o idioma da sessão (pt) sem a reserva do
 * idioma, não acha conteúdo pt e falha.
 *
 * Correção: o conteúdo pt passa a apontar para a mesma mídia do en (o documento sai em inglês, como
 * no exemplo da SAP). O en não muda e o modelo que já tiver pt fica como está.
 *
 * ETAPA:
 *  'conferir' : mostra a situação, aplica e gera os documentos de uma remessa numa transação
 *               desfeita (rodar com commit desligado). Não grava nada.
 *  'aplicar'  : backup em /backup/documentos-impressao-pt-antes-<data>.txt e gravação (commit ligado).
 *
 * Aplicado em 03/11/2026 (backup /backup/documentos-impressao-pt-antes-20261103-200102.txt).
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.servicelayer.session.SessionExecutionBody

def ETAPA = 'conferir'
def REMESSA = 'cons00024009_0'   // remessa usada no teste da etapa conferir

def CODES = ['pack-template', 'pick-slip-template', 'consolidated-pick-slip-template', 'shipping-label-template',
	'return-shipping-label-template', 'export-form-template', 'return-form-template',
	'electronics-spa-CMSImageComponent-template', 'electronics-spa-BannerComponent-template',
	'electronics-spa-SimpleBannerComponent-template', 'electronics-spa-CMSLinkComponent-template',
	'electronics-spa-CMSParagraphComponent-template']
def DOCUMENTOS = ['PickLabelDocumentTemplate', 'PackLabelDocumentTemplate', 'ShippingLabelDocumentTemplate',
	'ExportFormDocumentTemplate', 'ReturnShippingLabelDocumentTemplate', 'ReturnFormDocumentTemplate']

def out = []
def fs = flexibleSearchService
def pt = new Locale('pt')
def en = new Locale('en')
def ctx = de.hybris.platform.core.Registry.getCoreApplicationContext()

def proc = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {ConsignmentProcess} WHERE {code}=?c",
		[c: REMESSA + '_ordermanagement'])).result[0]
def gerar = { String tpl ->
	if (proc == null) return 'sem o processo da remessa ' + REMESSA
	sessionService.executeInLocalView(new SessionExecutionBody() {
		Object execute() {
			commonI18NService.currentLanguage = commonI18NService.getLanguage('pt')
			try {
				def m = ctx.getBean('printMediaService').getMediaForTemplate(tpl, proc)
				return 'ok (' + m?.size + ' bytes)'
			} catch (Throwable e) {
				return 'ERRO ' + e.class.simpleName + ': ' + e.message
			}
		}
	})
}

if (ETAPA == 'conferir') out << 'antes, documento de retirada: ' + gerar('PickLabelDocumentTemplate')

def linhas = []
CODES.each { c ->
	def t = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {RendererTemplate} WHERE {code}=?c", [c: c])).result[0]
	if (t == null) { out << c + ': não existe'; return }
	linhas << c + ' | pt antes: ' + t.getContent(pt)?.code + ' | en: ' + t.getContent(en)?.code
	if (t.getContent(pt) == null && t.getContent(en) != null) {
		t.setContent(t.getContent(en), pt)
		modelService.save(t)
		out << c + ': pt -> ' + t.getContent(pt).code
	} else {
		out << c + ': já tinha pt (' + t.getContent(pt)?.code + '), não mexi'
	}
}

if (ETAPA == 'conferir') {
	DOCUMENTOS.each { out << 'depois, ' + it + ': ' + gerar(it) }
	out << 'conferência: nada gravado se o commit estiver desligado'
} else if (ETAPA == 'aplicar') {
	def bk = new File('/backup/documentos-impressao-pt-antes-' + new java.text.SimpleDateFormat('yyyyMMdd-HHmmss').format(new Date()) + '.txt')
	bk.parentFile.mkdirs()
	bk.text = linhas.join('\n') + '\n'
	out << 'backup: ' + bk.path
}
return out.join('\n')
