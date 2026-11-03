/*
 * Item 13 do plano de evolução (ato 7.4 do roteiro): liga a Pesquisa adaptável (Adaptive Search)
 * na busca da loja. O índice da loja (electronicsIndex, tipo electronicsProductType) usa o
 * catálogo flamengoProductCatalog, que não tinha perfil de busca: o cockpit Pesquisa adaptável
 * não tinha onde ajustar. Cria o perfil "Loja Flamengo" (por categoria, catálogo Online) e o
 * conjunto de ativação do índice. Sem regras: a busca da loja continua igual até alguém
 * impulsionar, fixar ou ocultar um produto no cockpit.
 *
 * Rodar no HAC (Groovy), primeiro commit=false. Para voltar: apagar o perfil flamengoLoja e o
 * conjunto de ativação electronicsProductType/flamengoProductCatalog:Online.
 */
import de.hybris.platform.adaptivesearch.model.AsCategoryAwareSearchProfileModel
import de.hybris.platform.adaptivesearch.model.AsSearchProfileActivationSetModel
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def out = []
def cv = catalogVersionService.getCatalogVersion('flamengoProductCatalog', 'Online')
def one = { String q, Map p -> def r = flexibleSearchService.search(new FlexibleSearchQuery(q, p)).result; r ? r[0] : null }
def p = one("SELECT {pk} FROM {AsCategoryAwareSearchProfile} WHERE {code}='flamengoLoja' AND {catalogVersion}=?cv", [cv: cv])
if (p == null) {
	p = modelService.create(AsCategoryAwareSearchProfileModel)
	p.code = 'flamengoLoja'
	p.catalogVersion = cv
	p.indexType = 'electronicsProductType'
}
p.setName('Loja Flamengo', new Locale('pt'))
p.setName('Flamengo store', Locale.ENGLISH)
modelService.save(p)
out << 'Perfil ' + p.code + ' (' + p.indexType + ', ' + cv.catalog.id + ':' + cv.version + ')'
def a = one("SELECT {pk} FROM {AsSearchProfileActivationSet} WHERE {indexType}='electronicsProductType' AND {catalogVersion}=?cv", [cv: cv])
if (a == null) {
	a = modelService.create(AsSearchProfileActivationSetModel)
	a.indexType = 'electronicsProductType'
	a.catalogVersion = cv
	a.priority = 0
}
a.searchProfiles = [p]
modelService.save(a)
out << 'Ativação: ' + a.indexType + ' / ' + cv.version + ' -> ' + a.searchProfiles*.code
return out.join('\n')
