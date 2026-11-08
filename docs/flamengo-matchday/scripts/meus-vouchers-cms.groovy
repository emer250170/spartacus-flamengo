// Meus vouchers (08/11): página /my-account/vouchers e item "Meus vouchers" no menu Minha conta (header),
// logo depois de "Meus pedidos". electronics-spaContentCatalog, Staged e Online. Rodar DEPOIS de publicar a loja
// com o FlaMyVouchersComponent (senão a página abre vazia).
// MODO 'conferir' (commit=false): mostra o que existe e o que vai ser criado. MODO 'aplicar' (commit=true): cria.
// Idempotente: o que já existe fica como está. Para desfazer: MODO 'remover' (commit=true) apaga só o que este
// script cria (página, slots, componente, link, nó e entrada do menu).
import de.hybris.platform.cms2.model.contents.components.CMSFlexComponentModel
import de.hybris.platform.cms2.model.contents.components.CMSLinkComponentModel
import de.hybris.platform.cms2.model.contents.contentslot.ContentSlotModel
import de.hybris.platform.cms2.model.navigation.CMSNavigationEntryModel
import de.hybris.platform.cms2.model.navigation.CMSNavigationNodeModel
import de.hybris.platform.cms2.model.pages.ContentPageModel
import de.hybris.platform.cms2.model.relations.ContentSlotForPageModel
import de.hybris.platform.cms2.enums.CmsApprovalStatus
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def MODO = 'conferir'
userService.setCurrentUser(userService.getAdminUser())
def PT = new Locale('pt'); def EN = Locale.ENGLISH
def one = { String type, cv, String uid ->
  def q = new FlexibleSearchQuery("SELECT {pk} FROM {" + type + "} WHERE {catalogVersion}=?cv AND {uid}=?u")
  q.addQueryParameter('cv', cv); q.addQueryParameter('u', uid)
  def r = flexibleSearchService.search(q).result
  r ? r[0] : null
}
def out = []
['Staged', 'Online'].each { ver ->
  def cv = catalogVersionService.getCatalogVersion('electronics-spaContentCatalog', ver)
  def comp = one('CMSFlexComponent', cv, 'FlaMyVouchersComponent')
  def page = one('ContentPage', cv, 'my-vouchers')
  def body = one('ContentSlot', cv, 'BodyContent-my-vouchers')
  def side = one('ContentSlot', cv, 'SideContent-my-vouchers')
  def link = one('CMSLinkComponent', cv, 'MyVouchersLink')
  def node = one('CMSNavigationNode', cv, 'MyVouchersNavNode')
  def entry = one('CMSNavigationEntry', cv, 'MyVouchersNavNodeEntry')
  def parent = one('CMSNavigationNode', cv, 'MyAccountNavNode')
  def tpl = one('PageTemplate', cv, 'AccountPageTemplate')
  out << "${ver}: componente=${comp != null} página=${page != null} slots=${body != null}/${side != null} link=${link != null} nó=${node != null} entrada=${entry != null} | menu=${parent?.uid} modelo=${tpl?.uid}"
  if (MODO == 'remover') {
    [entry, node, link].findAll { it }.each { modelService.remove(it) }
    if (page) {
      def q = new FlexibleSearchQuery("SELECT {pk} FROM {ContentSlotForPage} WHERE {page}=?p"); q.addQueryParameter('p', page)
      flexibleSearchService.search(q).result.each { modelService.remove(it) }
      modelService.remove(page)
    }
    [body, side, comp].findAll { it }.each { modelService.remove(it) }
    out << "  removido"
    return
  }
  if (MODO != 'aplicar') return
  if (!parent || !tpl) { out << "  FALTA MyAccountNavNode ou AccountPageTemplate: nada feito"; return }
  if (!comp) {
    comp = modelService.create(CMSFlexComponentModel); comp.catalogVersion = cv; comp.uid = 'FlaMyVouchersComponent'
    comp.name = 'Meus vouchers'; comp.flexType = 'FlaMyVouchersComponent'; modelService.save(comp)
  }
  if (!body) {
    body = modelService.create(ContentSlotModel); body.catalogVersion = cv; body.uid = 'BodyContent-my-vouchers'
    body.name = 'Meus vouchers BodyContent'; body.active = true; body.cmsComponents = [comp]; modelService.save(body)
  }
  if (!side) {
    side = modelService.create(ContentSlotModel); side.catalogVersion = cv; side.uid = 'SideContent-my-vouchers'
    side.name = 'Meus vouchers SideContent'; side.active = true; modelService.save(side)
  }
  if (!page) {
    page = modelService.create(ContentPageModel); page.catalogVersion = cv; page.uid = 'my-vouchers'
    page.name = 'Meus vouchers'; page.label = '/my-account/vouchers'; page.masterTemplate = tpl
    page.defaultPage = true; page.homepage = false; page.approvalStatus = CmsApprovalStatus.APPROVED
    page.setTitle('Meus vouchers', PT); page.setTitle('My vouchers', EN); modelService.save(page)
    [['BodyContent', body], ['SideContent', side]].each { pos, slot ->
      def r = modelService.create(ContentSlotForPageModel); r.catalogVersion = cv
      r.uid = pos + '-my-vouchers'; r.position = pos; r.page = page; r.contentSlot = slot; modelService.save(r)
    }
  }
  if (!link) {
    link = modelService.create(CMSLinkComponentModel); link.catalogVersion = cv; link.uid = 'MyVouchersLink'
    link.name = 'Link Meus vouchers'; link.url = '/my-account/vouchers'
    link.setLinkName('Meus vouchers', PT); link.setLinkName('My vouchers', EN); modelService.save(link)
  }
  if (!node) {
    node = modelService.create(CMSNavigationNodeModel); node.catalogVersion = cv; node.uid = 'MyVouchersNavNode'
    node.name = 'Meus vouchers'; node.parent = parent; node.visible = true; modelService.save(node)
    // logo depois de "Meus pedidos"
    def kids = new ArrayList(parent.children)
    kids.remove(node)
    def i = kids.findIndexOf { it.uid == 'OrderHistoryNavNode' }
    kids.add(i >= 0 ? i + 1 : kids.size(), node)
    parent.children = kids; modelService.save(parent)
  }
  if (!entry) {
    entry = modelService.create(CMSNavigationEntryModel); entry.catalogVersion = cv; entry.uid = 'MyVouchersNavNodeEntry'
    entry.name = 'Meus vouchers'; entry.navigationNode = node; entry.item = link; modelService.save(entry)
  }
  modelService.refresh(parent)
  out << "  menu agora: " + parent.children.collect { it.uid }.join(', ')
}
out.join('\n')
