// Confirmação de pedido com a cara do Flamengo (HAC › Console › Scripting, Groovy).
// Gerado por docs/flamengo-matchday/emails/gerar.py a partir de confirmacao-pedido-corpo.vm.
// MODO 'previa'  : cria o modelo só na memória (rode com commit=false) e devolve o HTML do e-mail
//                  de um pedido real (PEDIDO_PREVIA). Nada é gravado.
// MODO 'aplicar' : grava os RendererTemplates fla_Email_Order_Confirmation_Body/_Subject e liga no
//                  OrderConfirmationEmailTemplate do electronics-spaContentCatalog (Staged e Online).
//                  Rode com commit=true. Para voltar: RESTAURAR (abaixo) liga de novo os modelos antigos.
import de.hybris.platform.commons.model.renderer.RendererTemplateModel
import de.hybris.platform.commons.enums.RendererTypeEnum
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def MODO = '@MODO@'
def PEDIDO_PREVIA = '@PEDIDO@'
def CORPO = new String('@CORPO_B64@'.decodeBase64(), 'UTF-8')
def ASSUNTO = 'Pedido ${ctx.order.code} recebido · Loja Flamengo'
def CONTEXTO = 'de.hybris.platform.yacceleratorfacades.process.email.context.OrderNotificationEmailContext'
def ANTIGOS = [corpo: 'electronics_Email_Order_Confirmation_Body', assunto: 'electronics_Email_Order_Confirmation_Subject']
def PT = new Locale('pt'), EN = Locale.ENGLISH
def fs = spring.getBean('flexibleSearchService')
def out = new StringBuilder("modo ${MODO}\n")

def modelo = { String code, String script ->
  def ex = new RendererTemplateModel(); ex.code = code
  def achado = fs.getModelsByExample(ex)
  def t = achado ? achado[0] : modelService.create(RendererTemplateModel)
  t.code = code; t.contextClass = CONTEXTO; t.rendererType = RendererTypeEnum.VELOCITY
  t.setTemplateScript(script, PT); t.setTemplateScript(script, EN)
  t
}
def corpo = modelo('fla_Email_Order_Confirmation_Body', CORPO)
def assunto = modelo('fla_Email_Order_Confirmation_Subject', ASSUNTO)

def paginas = ['Staged', 'Online'].collect { v ->
  def cv = catalogVersionService.getCatalogVersion('electronics-spaContentCatalog', v)
  def q = new FlexibleSearchQuery("SELECT {pk} FROM {EmailPage} WHERE {catalogVersion}=?cv AND {uid}='OrderConfirmationEmail'")
  q.addQueryParameter('cv', cv); fs.search(q).result[0]
}

if (MODO == 'previa') {
  def q = new FlexibleSearchQuery("SELECT {p.pk} FROM {OrderProcess AS p JOIN Order AS o ON {p.order}={o.pk}} WHERE {o.code}=?c AND {p.processDefinitionName}='orderConfirmationEmailProcess' ORDER BY {p.creationtime} DESC")
  q.addQueryParameter('c', PEDIDO_PREVIA)
  def proc = fs.search(q).result[0]
  def pagina = paginas[1]
  // a fábrica de contexto de e-mail só acha os beans no contexto do núcleo: renderiza numa thread
  // fora do contexto web do HAC, como o processo de e-mail faz
  def resultado = [:]
  def th = new Thread({
    try {
      de.hybris.platform.core.Registry.activateMasterTenant()
      // com o cliente do pedido (e não o admin) as restrições de catálogo valem, como no processo real
      def us = spring.getBean('userService'); us.setCurrentUser(proc.order.user)
      // como no processo de e-mail: site na sessão (catálogos Online, idioma e moeda do site)
      spring.getBean('baseSiteService').setCurrentBaseSite(proc.order.site, true)
      if (proc.order.language) { spring.getBean('commonI18NService').setCurrentLanguage(proc.order.language) }
      def ctx = spring.getBean('emailContextFactory').create(proc, pagina, corpo)
      // o modelo ainda não está gravado (o rendererService lê do banco): Velocity direto, com o
      // mesmo contexto do e-mail, publicado como $ctx
      def ve = new org.apache.velocity.app.VelocityEngine(); ve.init()
      def vc = new org.apache.velocity.VelocityContext(); vc.put('ctx', ctx)
      def w = new StringWriter(); ve.evaluate(vc, w, 'fla-corpo', CORPO)
      def ws = new StringWriter(); ve.evaluate(vc, ws, 'fla-assunto', ASSUNTO)
      resultado.texto = 'ASSUNTO: ' + ws.toString().trim() + ' | para: ' + ctx.email + ' (' + ctx.displayName + ')\n@@HTML@@\n' + w.toString()
    } catch (Throwable e) { def sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw)); resultado.texto = 'ERRO: ' + sw.toString().take(3000) }
    finally { de.hybris.platform.core.Registry.unsetCurrentTenant() }
  } as Runnable)
  th.start(); th.join(60000)
  return resultado.texto
}

if (MODO == 'aplicar' || MODO == 'restaurar') {
  def usar = MODO == 'aplicar' ? [corpo, assunto] : [ANTIGOS.corpo, ANTIGOS.assunto].collect { c -> def ex = new RendererTemplateModel(); ex.code = c; fs.getModelsByExample(ex)[0] }
  if (MODO == 'aplicar') { modelService.saveAll(corpo, assunto) }
  paginas.each { p ->
    def tpl = p.masterTemplate
    out << "${p.catalogVersion.version}: ${tpl.uid} corpo ${tpl.htmlTemplate?.code} -> ${usar[0].code}, assunto ${tpl.subject?.code} -> ${usar[1].code}\n"
    tpl.htmlTemplate = usar[0]; tpl.subject = usar[1]; modelService.save(tpl)
    p.setFromName(MODO == 'aplicar' ? 'Loja Flamengo' : null, PT); modelService.save(p)
  }
  return out.toString()
}
return out << 'MODO desconhecido\n' as String
