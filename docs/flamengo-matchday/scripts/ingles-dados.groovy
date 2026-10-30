// Loja em ingles: textos lang=en dos produtos do matchday e do socio-torcedor, dos jogos,
// das categorias, do menu e dos carrosseis da home. HAC > Console > Scripting, Groovy, commit.
// Rodado em 30/10. Valor anterior de cada campo em /backup/antes-ingles-20261030/data-en-antes.txt
// Os nomes em ingles das poltronas (Flamengo x Bahia · North · A9) estao no fim, em segundo plano.

// Textos em ingles (lang=en) da loja Flamengo. Guarda o valor anterior em /backup/antes-ingles-20261030/data-en-antes.txt
def fs = spring.getBean('flexibleSearchService'); def ms = spring.getBean('modelService')
def en = new Locale('en'); def pt = new Locale('pt')
def log = new StringBuilder(); def bak = new StringBuilder()
def setEn = { item, String attr, String value ->
  def old = item.getProperty(attr, en)
  if (old != value) {
    bak << item.itemtype + "|" + (item.hasProperty('code') ? item.code : item.uid) + "|" + item.catalogVersion?.version + "|" + attr + "|" + (old == null ? "<null>" : old.replace('\n','\\n')) + "\n"
    item.setProperty(attr, en, value); return 1
  }
  return 0
}
def products = { String code -> fs.search("SELECT {pk} FROM {Product} WHERE {code}=?c", [c: code]).result }
int n = 0
// 1) produtos do matchday e do socio-torcedor
def P = [
 'FLA-EST-MARACANA': [summary: '300 m from the gate · guaranteed spot', description: 'Guaranteed spot at the Maracanã parking lot on matchday. The plate is read at the entrance, no paper.'],
 'FLA-EST-UERJ': [summary: '600 m from the gate · quick exit', description: 'Guaranteed spot at the UERJ parking lot on matchday. The plate is read at the entrance, no paper.'],
 'FLA-EST-MOTO': [summary: 'Motorcycle area next to gate 18', description: 'Motorcycle spot next to gate 18 on matchday.'],
 'FLA-FOOD-COMBO': [summary: 'Delivered to your seat or picked up at the kiosk', description: 'Craft burger with a can of soda.'],
 'FLA-FOOD-CERVEJA': [summary: 'Delivered to your seat or picked up at the kiosk', description: '473 ml can of beer. Not sold to anyone under 18.'],
 'FLA-FOOD-AGUA': [summary: 'Delivered to your seat or picked up at the kiosk', description: '500 ml still mineral water.'],
 'FLA-FOOD-PIPOCA': [summary: 'Delivered to your seat or picked up at the kiosk', description: 'Salted popcorn, large bucket.'],
 'FLA-FOOD-PAOQUEIJO': [summary: 'Delivered to your seat or picked up at the kiosk', description: 'Six warm cheese breads.'],
 'FLA-PERSONALIZACAO': [summary: 'Name and number on the back', description: 'Official jersey personalization with name and number, picked up at the stadium on matchday.'],
 'FLA-CAMISA-J2-2026': [summary: 'Personalize it and pick it up at the stadium on matchday', description: 'The new 2026 away jersey. Personalize it with name and number and pick it up at the stadium on matchday.'],
 'NACAO-MARACA-1': [summary: 'Priority 1 · 75% off full-price tickets · guaranteed tickets', description: '<ul><li>Priority 1 to buy tickets for matches at Maracanã and away matches</li><li>75% off the full ticket price</li><li>Guaranteed tickets for Flamengo home matches at Maracanã in your priority wave</li><li>Up to 3 guests at R$ 60.00/month each, buying with the same priority as the member</li><li>Rating that rewards membership time</li><li>Right to buy the annual Maracanã package</li></ul>'],
 'NACAO-MARACA-2': [summary: 'Priority 2 · 65% off full-price tickets · 10% of tickets reserved', description: '<ul><li>Priority 2 to buy tickets for matches at Maracanã and away matches</li><li>65% off the full ticket price</li><li>10% of the tickets reserved in your priority wave for home matches at Maracanã</li><li>Up to 2 guests at R$ 60.00/month each, buying with the same priority as the member</li><li>Rating that rewards Maracanã attendance and membership time</li><li>Right to buy the annual Maracanã package</li></ul>'],
 'NACAO-MARACA-3': [summary: 'Priority 3 · 60% off full-price tickets · 10% of tickets reserved', description: '<ul><li>Priority 3 to buy tickets for matches at Maracanã and away matches</li><li>60% off the full ticket price</li><li>10% of the tickets reserved in your priority wave for home matches at Maracanã</li><li>1 guest at R$ 35.00/month, buying with the same priority as the member</li><li>Rating that rewards membership time</li><li>Right to buy the annual Maracanã package</li></ul>'],
 'NACAO-CONVIDADO-60': [name: 'Nação Maraca 1 & 2 guest', summary: 'Guest of a Nação Maraca 1 or 2 member · buys with the same priority', description: '<p>Guest of a Nação Maraca 1 or 2 member · buys with the same priority</p>'],
 'NACAO-CONVIDADO-35': [name: 'Nação Maraca 3 guest', summary: 'Guest of a Nação Maraca 3 member · buys with the same priority', description: '<p>Guest of a Nação Maraca 3 member · buys with the same priority</p>'],
]
P.each { code, vals -> products(code).each { p -> vals.each { a, v -> n += setEn(p, a, v) }; ms.save(p) } }
log << "produtos matchday/socio: " + n + " campos\n"
// 2) jogos sem ondas: descricao em ingles a partir do nome em ingles
n = 0
['JOGO-20261019-PAL','JOGO-20261022-RAC','JOGO-20261101-FLU'].each { code -> products(code).each { p ->
  def parts = p.getName(en).split(' · ')
  def descPt = p.getDescription(pt) ?: ''
  def presale = descPt.contains('Pré-venda para sócios') ? ' Member presale.' : ''
  n += setEn(p, 'description', parts[0] + ', ' + parts[1] + ', ' + parts[2] + ', at Maracanã.' + presale + ' Pick your sector and seat and build your matchday: parking, food and drinks and the new jersey in the same order.')
  n += setEn(p, 'summary', (p.getSummary(pt) ?: '').replaceAll(/\bRodada\b/, 'Round'))
  ms.save(p)
} }
products('JOGO-20261115-BAH').each { p -> n += setEn(p, 'summary', (p.getSummary(pt) ?: '').replaceAll(/\bRodada\b/, 'Round')); ms.save(p) }
log << "jogos: " + n + " campos\n"
// 4) categorias, navegacao e links do menu
def T = ['Camisas Personalizadas':'Custom Jerseys','Acessórios':'Accessories','Linha Adidas':'Adidas Line','Linha Baby':'Baby Line','Feminino':'Women','Roupas':'Clothing','Jogo 1':'Home Kit','Jogo 2':'Away Kit','Jogo 3':'Third Kit','Treino e Viagem':'Training & Travel','Masculino':'Men','Infantil':'Kids','Camisas':'Shirts','Feminino Personalizados':'Women\'s Custom','Jaquetas':'Jackets','Calças':'Pants','Calçados':'Footwear','Regatas':'Tank Tops','Mantos':'Jerseys','Camisas Oficiais':'Official Jerseys','Cachecol':'Scarves','Camisas Diversas':'Other Shirts','Criança':'Children','Infantil Personalizados':'Kids\' Custom','Masculino Personalizados':'Men\'s Custom','Outros':'Others','Chinelos':'Flip-flops','Tênis':'Sneakers','Personalizáveis':'Customizable','Utensílios':'Housewares','Bolas':'Balls','Personalização':'Personalization','Carteiras':'Wallets','Cropped':'Crop Tops','Camisas Polos':'Polo Shirts','Polos':'Polos','Meiões':'Socks','Goleiro':'Goalkeeper','Estojos':'Pencil Cases','Relógios':'Watches','Bolsas':'Bags','Bonés':'Caps','Mochilas':'Backpacks','Papelaria':'Stationery','Linha Pet':'Pet Line','Chaveiros':'Keychains','Bandeiras e Flâmulas':'Flags & Pennants','Roupas Diversas':'Other Clothing','Copos':'Cups','Canecas':'Mugs','Garrafas e Squeeze':'Bottles','Tamanho':'Size','Categorias Flamengo':'Flamengo Categories','Shorts':'Shorts','PP':'XS','P':'S','G':'L','GG':'XL','XGG':'XXL','2GG':'2XL','3GG':'3XL','4GG':'4XL','RN':'Newborn']
def tr = { String s -> s == null ? null : (T[s] ?: (s =~ /^(\d+) anos$/ ? s.replaceAll(/^(\d+) anos$/, '$1 years') : null)) }
n = 0
fs.search("SELECT {c.pk} FROM {Category AS c JOIN CatalogVersion AS cv ON {c.catalogVersion}={cv.pk} JOIN Catalog AS k ON {cv.catalog}={k.pk}} WHERE {k.id}='flamengoProductCatalog'").result.each { c ->
  def v = tr(c.getName(pt)); if (v && (c.getName(en) == null || c.getName(en) == c.getName(pt))) { n += setEn(c, 'name', v); ms.save(c) }
}
log << "categorias: " + n + "\n"
n = 0
fs.search("SELECT {pk} FROM {CMSNavigationNode}").result.each { nd ->
  def v = tr(nd.getTitle(pt)); if (v && (nd.getTitle(en) == null || nd.getTitle(en) == nd.getTitle(pt))) { n += setEn(nd, 'title', v); ms.save(nd) }
}
fs.search("SELECT {pk} FROM {CMSLinkComponent} WHERE {uid} LIKE 'FlaC%'").result.each { l ->
  def v = tr(l.getLinkName(pt)); if (v && (l.getLinkName(en) == null || l.getLinkName(en) == l.getLinkName(pt))) { n += setEn(l, 'linkName', v); ms.save(l) }
}
def car = ['ElectronicsHomepageProductCarouselComponent':'Jerseys 2026','NewElectronicsHomepageProductCarouselComponent':'New in Store']
fs.search("SELECT {pk} FROM {ProductCarouselComponent}").result.each { c -> if (car[c.uid] && c.getTitle(pt) != 'null') { n += setEn(c, 'title', car[c.uid]); ms.save(c) } }
log << "navegacao/links/carrosseis: " + n + "\n"
def f = new File('/backup/antes-ingles-20261030/data-en-antes.txt'); f.parentFile.mkdirs(); f.append(bak.toString(), 'UTF-8')
log << "backup: " + bak.toString().readLines().size() + " linhas em " + f + "\n"

// ---- poltronas (segundo plano; resultado em /backup/antes-ingles-20261030/poltronas-en.log) ----
def ss = spring.getBean('sessionService'); def us = spring.getBean('userService')
def tenant = de.hybris.platform.core.Registry.getCurrentTenant()
Thread.start {
  de.hybris.platform.core.Registry.setCurrentTenant(tenant)
  def out = new File('/backup/antes-ingles-20261030/poltronas-en.log')
  try {
    ss.executeInLocalView(new de.hybris.platform.servicelayer.session.SessionExecutionBody() { Object execute() {
    us.setCurrentUser(us.getAdminUser())
    int n = 0; int total = 0
    fs.search("SELECT {pk} FROM {GenericVariantProduct} WHERE {code} LIKE 'JOGO-%-%-%'").result.each { v ->
      total++
      def namePt = v.getName(pt)
      if (!namePt) return
      def parts = namePt.split(' · ')
      def setor = v.supercategories.find { it.code?.startsWith('setor-') }
      if (parts.length == 3 && setor) {
        def nm = parts[0] + ' · ' + (setor.getName(en) ?: parts[1]) + ' · ' + parts[2]
        if (v.getName(en) != nm) { v.setName(nm, en); ms.save(v); n++ }
      }
    }
    out.text = "OK " + n + " de " + total + " poltronas em " + new Date()
    return null } })
  } catch (e) { out.text = "ERRO " + e }
  finally { de.hybris.platform.core.Registry.unsetCurrentTenant() }
}
return log.toString() + "poltronas: rodando em segundo plano"
