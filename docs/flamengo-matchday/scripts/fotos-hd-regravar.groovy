// Fotos nítidas (07/11): regrava nos produtos EXP-… e JOGO-… as fotos a partir da original enviada no
// Backoffice, com os tamanhos atuais (zoom com até 1600 px, chave flamengo.produtos.imagem.tamanho.515Wx515H).
// Rodar depois do ant all com o PR das fotos. APLICAR = false só lista as originais e o tamanho de cada uma.
import javax.imageio.ImageIO

def APLICAR = false

def svc = spring.getBean('flamengoProductImageService')
def out = new StringBuilder()
def tamanho = { media ->
  try {
    def img = ImageIO.read(new ByteArrayInputStream(mediaService.getDataFromMedia(media)))
    img == null ? '?' : img.width + 'x' + img.height
  } catch (e) { '? (' + e.message + ')' }
}
def tratar = { tipo, query ->
  flexibleSearchService.search(query).result.each { item ->
    def media = item.image
    def produto = item.product
    if (media == null || produto == null) {
      out << tipo << ' ' << item.code << ': sem foto ou sem produto\n'
      return
    }
    out << tipo << ' ' << item.code << ' -> ' << produto.code << ' (' << produto.catalogVersion.version << '): original '
    out << media.code << ' ' << tamanho(media)
    if (APLICAR) {
      out << ' | ' << svc.copyToProduct(media, produto)
    }
    out << '\n'
  }
}
tratar('Experiência', 'SELECT {pk} FROM {SocioExperience}')
tratar('Jogo', 'SELECT {pk} FROM {MatchdayGame}')
return out.toString()
