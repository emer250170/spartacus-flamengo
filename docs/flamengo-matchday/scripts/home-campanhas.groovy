// Destaques da home no modelo "campanha" (08/11): banner novo do Mengo Estúdio no lugar do "Manto 3" e o
// "Coleção Adidas 2026" (Manto Sagrado) refeito no mesmo modelo. Staged e Online do electronics-spaContentCatalog.
// Rodar DEPOIS de publicar a loja com o PR dos banners (o modelo campanha e a foto do Manto em assets/vitrine).
// APLICAR = false: só mostra o que existe e o que vai mudar (a transação é desfeita).
// Para voltar: carrossel FlaHomeHeroComponent com FlaHomeHeroManto3,FlaHomeHeroColecao e o conteúdo do
// FlaHomeHeroColecao como em 12-home-destaques.impex (o FlaHomeHeroManto3 não é apagado).
import de.hybris.platform.core.model.media.MediaModel
import de.hybris.platform.cms2lib.model.components.BannerComponentModel
import de.hybris.platform.cms2lib.model.components.RotatingImagesComponentModel
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def APLICAR = false
def LOJA = 'https://spartacus-flamengo.democc.online'
def PT = new Locale('pt'), EN = Locale.ENGLISH
userService.setCurrentUser(userService.getAdminUser())
def out = new StringBuilder()

def fotos = [
  'fla-hero-estudio': LOJA + '/assets/estudio/modelo-camiseta-branca-urubu.webp',
  'fla-hero-manto-sagrado': LOJA + '/assets/vitrine/manto-sagrado-jogo1-recorte.webp',
]
def bytes = [:]
fotos.each { code, url ->
  def c = new URL(url).openConnection(); c.connectTimeout = 10000; c.readTimeout = 20000
  def b = c.inputStream.bytes
  out << 'foto ' << code << ': ' << b.length << ' bytes (' << c.contentType << ')\n'
  if (b.length < 5000 || !(c.contentType ?: '').startsWith('image')) { throw new IllegalStateException('foto ' + url + ' n\u00e3o veio (loja publicada com o PR?)') }
  bytes[code] = b
}

def ESTUDIO_PT = '<p>Layout: campanha</p><p>Selo: Novidade \u00b7 Mengo Est\u00fadio</p><p>Ao vivo: Drop no ar</p><p>Texto: Escolha a estampa, monte a camiseta do seu jeito e receba uma pe\u00e7a feita s\u00f3 para voc\u00ea: numerada, com selo de autenticidade.</p><p>Bot\u00e3o: Criar minha camiseta</p><p>Bot\u00e3o 2: Ver as estampas | /mengo-estudio</p><p>Nota: Cada estampa sai de linha na sua data. Depois, s\u00f3 no acervo.</p><p>Fundo: MENGO / EST\u00daDIO</p><p>Carimbo: S\u00f3 tem uma. / \u00c9 sua.</p><p>Etiqueta: Selo de autenticidade / N\u00ba 0001</p>'
def ESTUDIO_EN = '<p>Layout: campanha</p><p>Selo: New \u00b7 Mengo Est\u00fadio</p><p>Ao vivo: Drop is live</p><p>Texto: Pick a print, build your T-shirt your way and get a piece made just for you: numbered, with a seal of authenticity.</p><p>Bot\u00e3o: Create my T-shirt</p><p>Bot\u00e3o 2: See the prints | /mengo-estudio</p><p>Nota: Each print leaves the line on its date. After that, only in the archive.</p><p>Fundo: MENGO / EST\u00daDIO</p><p>Carimbo: One of one. / Yours.</p><p>Etiqueta: Seal of authenticity / N\u00ba 0001</p>'
def MANTO_PT = '<p>Layout: campanha</p><p>Selo: Cole\u00e7\u00e3o Adidas 2026</p><p>Destaque: Jogo 1 \u00b7 Jogo 2 \u00b7 Jogo 3</p><p>Texto: Listras rubro-negras que fazem a Na\u00e7\u00e3o cantar mais alto. Vista o Manto 2026 e leve o Maracan\u00e3 no peito, onde voc\u00ea estiver.</p><p>Bot\u00e3o: Garantir o meu Manto</p><p>Bot\u00e3o 2: Ver o Jogo 1 | /product/flamengo_4899</p><p>Nota: S\u00f3cio Na\u00e7\u00e3o Maraca paga menos no Manto.</p><p>Fundo: MANTO / SAGRADO</p><p>Carimbo: Desde / 1895</p><p>Etiqueta: Manto Jogo 1 / 2026</p><p>Listras: horizontais</p>'
def MANTO_EN = '<p>Layout: campanha</p><p>Selo: Adidas 2026 collection</p><p>Destaque: Home \u00b7 Away \u00b7 Third</p><p>Texto: Red-and-black hoops that make the Na\u00e7\u00e3o sing louder. Wear the 2026 jersey and carry the Maracan\u00e3 on your chest, wherever you are.</p><p>Bot\u00e3o: Get my jersey</p><p>Bot\u00e3o 2: See the home kit | /product/flamengo_4899</p><p>Nota: Na\u00e7\u00e3o Maraca members pay less for the jersey.</p><p>Fundo: MANTO / SAGRADO</p><p>Carimbo: Since / 1895</p><p>Etiqueta: Home kit / 2026</p><p>Listras: horizontais</p>'

def um = { tipo, uid, cv ->
  def q = new FlexibleSearchQuery('SELECT {pk} FROM {' + tipo + '} WHERE {uid}=?u AND {catalogVersion}=?cv', [u: uid, cv: cv])
  def r = flexibleSearchService.search(q).result
  r ? r[0] : null
}
def midia = { code, cv ->
  def q = new FlexibleSearchQuery('SELECT {pk} FROM {Media} WHERE {code}=?c AND {catalogVersion}=?cv', [c: code, cv: cv])
  def r = flexibleSearchService.search(q).result
  def m = r ? r[0] : modelService.create(MediaModel)
  m.code = code; m.catalogVersion = cv; m.mime = 'image/webp'; m.realFileName = code + '.webp'
  modelService.save(m)
  mediaService.setStreamForMedia(m, new ByteArrayInputStream(bytes[code]), code + '.webp', 'image/webp')
  m
}

['Staged', 'Online'].each { v ->
  def cv = catalogVersionService.getCatalogVersion('electronics-spaContentCatalog', v)
  def rot = um('RotatingImagesComponent', 'FlaHomeHeroComponent', cv)
  def col = um('BannerComponent', 'FlaHomeHeroColecao', cv)
  def est = um('BannerComponent', 'FlaHomeHeroEstudio', cv)
  out << '\n== ' << v << '\nantes: carrossel=' << (rot?.banners?.collect { it.uid }) << ' | Colecao=' << col?.getHeadline(PT) << ' | Estudio existe=' << (est != null) << '\n'
  if (rot == null || col == null) { out << 'FALTA carrossel ou FlaHomeHeroColecao: nada feito\n'; return }
  if (!APLICAR) { out << 'teste: nada gravado\n'; return }

  def mEst = midia('fla-hero-estudio', cv)
  def mManto = midia('fla-hero-manto-sagrado', cv)

  if (est == null) { est = modelService.create(BannerComponentModel); est.uid = 'FlaHomeHeroEstudio'; est.catalogVersion = cv }
  est.name = 'Destaque da home - Mengo Est\u00fadio'
  est.setHeadline('Vista o que / voc\u00ea sente', PT); est.setHeadline('Wear what / you feel', EN)
  est.setContent(ESTUDIO_PT, PT); est.setContent(ESTUDIO_EN, EN)
  est.urlLink = '/mengo-estudio/camiseta'; est.external = false
  est.setMedia(mEst, PT); est.setMedia(mEst, EN)
  modelService.save(est)

  col.name = 'Destaque da home - Manto Sagrado (Cole\u00e7\u00e3o Adidas 2026)'
  col.setHeadline('O Manto / \u00e9 sagrado', PT); col.setHeadline('The jersey / is sacred', EN)
  col.setContent(MANTO_PT, PT); col.setContent(MANTO_EN, EN)
  col.urlLink = '/c/flamengo_c115'; col.external = false
  col.setMedia(mManto, PT); col.setMedia(mManto, EN)
  modelService.save(col)

  rot.banners = [est, col]
  modelService.save(rot)
  out << 'depois: carrossel=' << rot.banners.collect { it.uid } << ' | Estudio=' << est.getHeadline(PT) << ' | Colecao=' << col.getHeadline(PT) << ' | fotos=' << mEst.URL << ' , ' << mManto.URL << '\n'
}
out.toString()
