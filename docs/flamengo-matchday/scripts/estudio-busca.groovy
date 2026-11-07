// Mengo Estúdio (07/11): as camisetas do Estúdio (ME-CAM-*) não entram na busca da loja.
// Acrescenta "{code} NOT LIKE 'ME-CAM-%'" nas consultas do índice electronics (cheia e de atualização,
// com e sem visibilidade) e tira as camisetas que já estiverem no índice. Pode rodar mais de uma vez.
// APLICAR = false só mostra o que mudaria.
def APLICAR = false
def out = new StringBuilder()
def ms = spring.getBean('modelService')
def FILTRO_FULL = " AND {code} NOT LIKE 'ME-CAM-%'"
def FILTRO_UPD = " AND tbl.code NOT LIKE 'ME-CAM-%'"
['electronics-fullQuery', 'electronics-visibility-fullQuery', 'electronics-updateQuery', 'electronics-visibility-updateQuery'].each { id ->
  def q = flexibleSearchService.search("SELECT {pk} FROM {SolrIndexerQuery} WHERE {identifier} = '" + id + "'").result[0]
  if (q == null) { out << id << ': não encontrada\n'; return }
  def texto = q.query
  if (texto.contains("NOT LIKE 'ME-CAM-%'")) { out << id << ': já filtra\n'; return }
  def novo
  if (id.endsWith('fullQuery')) {
    novo = texto + FILTRO_FULL
  } else {
    def i = texto.lastIndexOf(' ORDER BY tbl.code')
    novo = i > 0 ? texto.substring(0, i) + FILTRO_UPD + texto.substring(i) : texto + FILTRO_UPD
  }
  out << id << ': passa a terminar com ...' << novo.substring(Math.max(0, novo.length() - 90)) << '\n'
  if (APLICAR) { q.query = novo; ms.save(q) }
}
if (APLICAR) {
  def fsc = spring.getBean('facetSearchConfigService').getConfiguration('electronicsIndex')
  def tipo = fsc.indexConfig.indexedTypes.values().find { it.code == 'Product' }
  def pks = flexibleSearchService.search("SELECT {pk} FROM {Product} WHERE {code} LIKE 'ME-CAM-%'").result*.pk
  if (pks) { spring.getBean('indexerService').deleteTypeIndex(fsc, tipo, pks); out << 'tiradas do índice: ' << pks.size() << '\n' }
}
return out.toString()
