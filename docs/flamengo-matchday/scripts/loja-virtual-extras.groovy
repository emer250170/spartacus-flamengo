/*
 * Item 13 do plano de evolução (ato 7.6 do roteiro): dados para a lista de desejos, o
 * "avise-me quando chegar" e o atendimento assistido (ASM).
 * Rodar no HAC (Groovy). Primeiro com commit=false (mostra o que muda), depois commit=true.
 *
 *  - Avise-me: o produto PRODUTO (Bola Flamengo Oficial Assinada pelo Zico, que não aparece nos
 *    atos 1 a 6) fica sem estoque no depósito da loja online e nas 3 Lojas Flamengo. Na página
 *    dele a loja mostra "Esgotado" e o botão do avise-me (torcedor logado).
 *  - ASM: atendente atendente.asm no grupo asagentsalesgroup (que está no asagentgroup), sem
 *    senha: o Emerson define no Backoffice. Na loja: acrescentar ?asm=true no endereço.
 *  - Lista de desejos: não precisa de dado.
 *
 * Backup do estoque em /backup/loja-virtual-extras-antes-<data>.txt. Para voltar: ACAO = 'repor'
 * (devolve o estoque de antes, REPOR por depósito, e tira o "esgotado").
 * Aplicado em 03/11/2026: antes havia 100 no warehouse_s e 10 em cada Loja Flamengo.
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.core.model.user.EmployeeModel
import de.hybris.platform.basecommerce.enums.InStockStatus

def ACAO = 'esgotar'     // 'esgotar' | 'repor'
def PRODUTO = 'flamengo_3095'
def REPOR = [warehouse_s: 100, barrastore: 10, copacabanastore: 10, maracanastore: 10]
def DEPOSITOS = ['warehouse_s', 'barrastore', 'copacabanastore', 'maracanastore']

def out = []
def fs = flexibleSearchService
def niveis = fs.search(new FlexibleSearchQuery(
		"SELECT {s.pk} FROM {StockLevel AS s JOIN Warehouse AS w ON {s.warehouse}={w.pk}} WHERE {s.productCode}=?p AND {w.code} IN (?w)",
		[p: PRODUTO, w: DEPOSITOS])).result

// ---------------------------------------------------------------- backup
def ts = new java.text.SimpleDateFormat('yyyyMMdd-HHmmss').format(new Date())
def dir = new File('/backup'); dir.mkdirs()
def bk = new File(dir, "loja-virtual-extras-antes-${ts}.txt")
bk.text = niveis.collect { "${it.warehouse.code};${it.productCode};available=${it.available};reserved=${it.reserved};status=${it.inStockStatus?.code}" }.join('\n') + '\n'
out << 'Backup: ' + bk.path

// ---------------------------------------------------------------- estoque do avise-me
niveis.each { s ->
	if (ACAO == 'esgotar') {
		s.available = 0
		s.inStockStatus = InStockStatus.FORCEOUTOFSTOCK
	} else {
		s.available = REPOR[s.warehouse.code] ?: 0
		s.inStockStatus = InStockStatus.NOTSPECIFIED
	}
	modelService.save(s)
	out << "${PRODUTO} em ${s.warehouse.code}: disponível ${s.available}, ${s.inStockStatus.code}"
}

// ---------------------------------------------------------------- atendente do ASM (sem senha)
if (ACAO == 'esgotar') {
	def uid = 'atendente.asm'
	def e = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {Employee} WHERE {uid}=?u", [u: uid])).result[0]
	if (e == null) {
		e = modelService.create(EmployeeModel)
		e.uid = uid
	}
	e.name = 'Atendente Nação (atendimento assistido)'
	e.loginDisabled = false
	e.groups = [userService.getUserGroupForUID('asagentsalesgroup')] as Set
	modelService.save(e)
	out << "ASM: ${e.uid} nos grupos " + e.allGroups*.uid.sort() + ' (sem senha)'
}
return out.join('\n')
