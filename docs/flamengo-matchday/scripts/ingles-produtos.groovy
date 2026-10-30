// Grava name/description em ingles dos produtos da loja Flamengo (Staged e Online) e dos tamanhos.
// Le /tmp/fla-en.json ({codigo: {name, desc}}). Backup em /backup/antes-ingles-20261030/produtos-en-antes.json
def fs = spring.getBean('flexibleSearchService'); def ms = spring.getBean('modelService')
def ss = spring.getBean('sessionService'); def us = spring.getBean('userService')
def en = new Locale('en'); def pt = new Locale('pt')
def SIZES = ['PP':'XS','P':'S','M':'M','G':'L','GG':'XL','XG':'XL','XGG':'XXL','2GG':'2XL','3GG':'3XL','4GG':'4XL','3G':'3XL','RN':'Newborn','UN':'One Size']
def size = { String s -> s == null ? s : (SIZES[s] ?: s.replaceAll(/^(\d+) anos$/, '$1 years').replaceAll(/^(\d+)A$/, '$1Y')) }
def tenant = de.hybris.platform.core.Registry.getCurrentTenant()
Thread.start {
  de.hybris.platform.core.Registry.setCurrentTenant(tenant)
  def log = new File('/backup/antes-ingles-20261030/produtos-en.log')
  try {
    ss.executeInLocalView(new de.hybris.platform.servicelayer.session.SessionExecutionBody() { Object execute() {
      us.setCurrentUser(us.getAdminUser())
      def d = new groovy.json.JsonSlurper().parse(new File('/tmp/fla-en.json'), 'UTF-8')
      def bak = [:]; int nb = 0; int nv = 0
      def prods = fs.search("SELECT {p.pk} FROM {Product AS p JOIN CatalogVersion AS cv ON {p.catalogVersion}={cv.pk} JOIN Catalog AS k ON {cv.catalog}={k.pk}} WHERE {k.id}='flamengoProductCatalog' AND {p.code} LIKE 'flamengo_%'").result
      def bases = prods.findAll { !(it instanceof de.hybris.platform.variants.model.VariantProductModel) }
      def vars = prods.findAll { it instanceof de.hybris.platform.variants.model.VariantProductModel }
      bases.each { p ->
        def t = d[p.code]; if (!t) return
        bak[p.code + '|' + p.catalogVersion.version] = [name: p.getName(en), desc: p.getDescription(en)]
        p.setName(t.name, en)
        if (t.desc) p.setDescription(t.desc, en)
        ms.save(p); nb++
      }
      vars.each { v ->
        def t = d[v.baseProduct?.code]; if (!t) return
        def namePt = v.getName(pt) ?: ''
        def i = namePt.lastIndexOf(' - ')
        def nm = i > 0 ? t.name + ' - ' + size(namePt.substring(i + 3).trim()) : t.name
        bak[v.code + '|' + v.catalogVersion.version] = [name: v.getName(en)]
        v.setName(nm, en); ms.save(v); nv++
      }
      new File('/backup/antes-ingles-20261030/produtos-en-antes.json').setText(groovy.json.JsonOutput.toJson(bak), 'UTF-8')
      log.text = "OK base=" + nb + " variantes=" + nv + " em " + new Date()
      return null } })
  } catch (e) { log.text = "ERRO " + e }
  finally { de.hybris.platform.core.Registry.unsetCurrentTenant() }
}
return "rodando em segundo plano: /backup/antes-ingles-20261030/produtos-en.log"

// ---- Depois (HAC, commit): os tamanhos (variantes) têm descrição própria; recebem a descrição em inglês do produto base ----
// def fs = spring.getBean('flexibleSearchService'); def ms = spring.getBean('modelService')
// def en = new Locale('en'); def pt = new Locale('pt')
// fs.search("SELECT {p.pk} FROM {VariantProduct AS p JOIN CatalogVersion AS cv ON {p.catalogVersion}={cv.pk} JOIN Catalog AS k ON {cv.catalog}={k.pk}} WHERE {k.id}='flamengoProductCatalog' AND {p.code} LIKE 'flamengo_%'").result.each { v ->
//   def ben = v.baseProduct?.getDescription(en)
//   if (v.getDescription(pt) && ben) { v.setDescription(ben, en); ms.save(v) }
// }
