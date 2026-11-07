// E-mails da Loja Flamengo (HAC › Console › Scripting, Groovy). Gerado por docs/flamengo-matchday/emails/gerar.py.
// MODO 'previa'   : renderiza os e-mails (SO = lista de páginas, vazio = todos) sem gravar nada (commit=false).
//                   Usa o processo real quando existe um no banco; senão um contexto de exemplo montado com o
//                   pedido PEDIDO (dados reais convertidos, campos do e-mail preenchidos com exemplo).
// MODO 'aplicar'  : grava os RendererTemplates fla_Email_*_Body/_Subject (pt e en) e liga nos EmailPageTemplates
//                   do electronics-spaContentCatalog (Staged e Online). commit=true.
// MODO 'restaurar': volta os EmailPageTemplates para os modelos electronics_Email_* originais. commit=true.
import de.hybris.platform.commons.model.renderer.RendererTemplateModel
import de.hybris.platform.commons.enums.RendererTypeEnum
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.core.Registry

def MODO = '@MODO@'
def PEDIDO = '@PEDIDO@'
def SO = '@SO@'.tokenize(',')
// [modelo da página, corpo original, assunto original, código novo, assunto novo, corpo (base64), processo real (FlexibleSearch ou '')]
def EMAILS = @EMAILS@
def PT = new Locale('pt'), EN = Locale.ENGLISH
def fs = spring.getBean('flexibleSearchService')
def out = new StringBuilder("modo ${MODO}\n")
def dec = { String b -> new String(b.decodeBase64(), 'UTF-8') }
def rt = { String code -> def ex = new RendererTemplateModel(); ex.code = code; def r = fs.getModelsByExample(ex); r ? r[0] : null }
def cvs = ['Staged', 'Online'].collect { catalogVersionService.getCatalogVersion('electronics-spaContentCatalog', it) }
def modeloPagina = { String uid, cv ->
  def q = new FlexibleSearchQuery("SELECT {pk} FROM {EmailPageTemplate} WHERE {catalogVersion}=?cv AND {uid}=?uid")
  q.addQueryParameter('cv', cv); q.addQueryParameter('uid', uid); fs.search(q).result[0]
}
def paginaDe = { tpl ->
  def q = new FlexibleSearchQuery("SELECT {pk} FROM {EmailPage} WHERE {masterTemplate}=?t")
  q.addQueryParameter('t', tpl); def r = fs.search(q).result; r ? r[0] : null
}

if (MODO == 'aplicar' || MODO == 'restaurar') {
  EMAILS.each { e ->
    def (uid, corpoOrig, assuntoOrig, codigo, assunto, corpoB64) = e
    def original = rt(corpoOrig)
    def usar
    if (MODO == 'aplicar') {
      def novo = { String code, String script ->
        def t = rt(code) ?: modelService.create(RendererTemplateModel)
        t.code = code; t.contextClass = original.contextClass; t.rendererType = RendererTypeEnum.VELOCITY
        t.setTemplateScript(script, PT); t.setTemplateScript(script, EN); modelService.save(t); t
      }
      usar = [novo(codigo + '_Body', dec(corpoB64)), novo(codigo + '_Subject', assunto)]
    } else {
      usar = [original, rt(assuntoOrig)]
    }
    cvs.each { cv ->
      def tpl = modeloPagina(uid, cv)
      if (!tpl) { out << "${cv.version} ${uid}: não encontrado\n"; return }
      out << "${cv.version} ${uid}: ${tpl.htmlTemplate?.code} -> ${usar[0].code}\n"
      tpl.htmlTemplate = usar[0]; tpl.subject = usar[1]; modelService.save(tpl)
    }
  }
  return out.toString()
}

if (MODO == 'previa') {
  def res = [:]
  def th = new Thread({
    try {
      Registry.activateMasterTenant()
      def q = new FlexibleSearchQuery("SELECT {pk} FROM {Order} WHERE {code}=?c AND {versionID} IS NULL")
      q.addQueryParameter('c', PEDIDO)
      def pedido = fs.search(q).result[0]
      spring.getBean('userService').setCurrentUser(pedido.user)
      spring.getBean('baseSiteService').setCurrentBaseSite(pedido.site, true)
      spring.getBean('commonI18NService').setCurrentLanguage(pedido.language ?: spring.getBean('commonI18NService').getLanguage('pt'))
      def orderData = spring.getBean('orderConverter').convert(pedido)
      // remessa: a do pedido, ou a mais recente que tiver
      def cons = pedido.consignments ? pedido.consignments.iterator().next() : fs.search("SELECT {pk} FROM {Consignment} ORDER BY {creationtime} DESC").result[0]
      def consData = spring.getBean('consignmentConverter').convert(cons)
      def prodCodigo = orderData.entries.find { !it.product.code.startsWith('JOGO-') }?.product?.code ?: orderData.entries[0].product.code
      def opcoes = [de.hybris.platform.commercefacades.product.ProductOption.BASIC, de.hybris.platform.commercefacades.product.ProductOption.PRICE, de.hybris.platform.commercefacades.product.ProductOption.IMAGES]
      def prodData = spring.getBean('productFacade').getProductForCodeAndOptions(prodCodigo, opcoes)
      def exemplo = [displayName: pedido.user.name, email: pedido.user.uid, title: null, guest: false,
        order: orderData, orderData: orderData, orderCode: orderData.code, orderGuid: orderData.guid,
        consignment: consData, canceledEntries: orderData.entries, refundedEntries: orderData.entries,
        refundAmount: orderData.totalPriceWithTax, pickUpDate: '14/11/2026', productData: prodData,
        token: 'exemplo', URLEncodedToken: 'exemplo', expiresInMinutes: 30, tokenCode: '482913', ttlSeconds: 300, maxAttempts: 3,
        couponNotificationType: 'COUPON_EFFECTIVE', couponTitle: '10% OFF em mantos', couponSummary: '10% de desconto em qualquer camisa oficial.',
        startDate: '07/11/2026', endDate: '30/11/2026']
      def ve = new org.apache.velocity.app.VelocityEngine(); ve.init()
      def txt = new StringBuilder()
      EMAILS.findAll { !SO || SO.contains(it[0]) }.each { e ->
        def (uid, corpoOrig, assuntoOrig, codigo, assunto, corpoB64, procQuery) = e
        def corpo = dec(corpoB64)
        def ctx = exemplo, fonte = 'exemplo'
        try {
          if (procQuery) {
            def pr = fs.search(procQuery).result
            if (pr) {
              def tplOnline = modeloPagina(uid, cvs[1])
              ctx = spring.getBean('emailContextFactory').create(pr[0], paginaDe(tplOnline), rt(corpoOrig))
              fonte = 'processo ' + pr[0].code
            }
          }
          def vc = new org.apache.velocity.VelocityContext(); vc.put('ctx', ctx)
          def w = new StringWriter(); ve.evaluate(vc, w, uid, corpo)
          def ws = new StringWriter(); ve.evaluate(vc, ws, uid + '-assunto', assunto)
          txt << '@@EMAIL@@' << uid << '|' << ws.toString().trim() << '|' << fonte << '\n' << w.toString() << '\n'
        } catch (Throwable t) {
          def sw = new StringWriter(); t.printStackTrace(new PrintWriter(sw))
          txt << '@@EMAIL@@' << uid << '|ERRO|' << fonte << '\n' << sw.toString().take(2500) << '\n'
        }
      }
      res.texto = txt.toString()
    } catch (Throwable t) { def sw = new StringWriter(); t.printStackTrace(new PrintWriter(sw)); res.texto = 'ERRO: ' + sw.toString().take(3000) }
    finally { Registry.unsetCurrentTenant() }
  } as Runnable)
  th.start(); th.join(120000)
  return res.texto
}
return out << 'MODO desconhecido\n' as String
