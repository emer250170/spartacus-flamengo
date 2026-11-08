// Atributo virtual "analise" do pedido na Integration API (objeto FlamengoPedido, item Order): um JSON com os dados
// de negócio do pedido já traduzidos, para relatórios e consultas do MCP "Loja Flamengo" (08/11).
// Sem dados pessoais: do endereço só cidade e UF; do cartão só a bandeira.
// Gravado como Script "flaAnalisePedido" (model://flaAnalisePedido) por integracao-analise-pedidos.impex.
// O Commerce passa o pedido na variável itemModel.
import groovy.json.JsonOutput
import de.hybris.platform.core.Registry
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def o = itemModel
def ctx = Registry.getApplicationContext()
def ms = ctx.getBean('modelService')
def fs = ctx.getBean('flexibleSearchService')
def get = { m, a ->
    def r = null
    try { r = ms.getAttributeValue(m, a) } catch (Exception ignored) { }
    return r
}
def pt = new Locale('pt')

// ---- pagamento
def socio = get(o, 'socioPaymentMethod')?.code
def adyen = String.valueOf(get(o, 'adyenPaymentMethod') ?: '').toLowerCase()
def pi = o.paymentInfo
def piCode = String.valueOf(pi?.code ?: '').toLowerCase()
def cartao = pi?.itemtype == 'CreditCardPaymentInfo'
def forma
if (socio == 'PIX_AUTOMATICO') forma = 'Pix Automático'
else if (socio == 'PIX' || adyen.startsWith('pix') || piCode.startsWith('pix')) forma = 'Pix'
else if (socio == 'BOLETO' || adyen.contains('boleto') || piCode.startsWith('boleto')) forma = 'Boleto'
else if (socio in ['CARTAO_MENSAL', 'CARTAO_ANUAL'] || adyen == 'scheme' || cartao) forma = 'Cartão de crédito'
else if (pi == null) forma = 'Sem pagamento registrado'
else forma = 'Outro'
def parcelas = get(o, 'socioInstallments')
def recorrencia = socio == 'CARTAO_MENSAL' ? 'mensal' : socio == 'CARTAO_ANUAL' ? 'anual' : socio == 'PIX_AUTOMATICO' ? 'mensal' : null
def provedor = o.paymentTransactions?.collect { it.paymentProvider }?.find { it }
def gateway = adyen ? 'Adyen' : (provedor && provedor != 'Mockup' ? provedor : (pi ? 'Simulado (demo)' : null))

// ---- entrega, cidade e UF (retirada: a cidade da loja)
def pos = o.entries?.collect { it.deliveryPointOfService }?.find { it }
def addr = pos?.address ?: o.deliveryAddress ?: o.paymentAddress
def uf = addr?.region?.isocodeShort ?: (addr?.region?.isocode ? addr.region.isocode.replaceFirst(/^[A-Z]{2}-/, '') : null)
if (!uf && addr?.postalcode) {
    // sem estado no endereço: UF pela faixa do CEP (o CEP em si não sai)
    def cep = (addr.postalcode.replaceAll(/\D/, '') + '00000').take(5) as int
    def faixas = [[1000, 19999, 'SP'], [20000, 28999, 'RJ'], [29000, 29999, 'ES'], [30000, 39999, 'MG'], [40000, 48999, 'BA'],
                  [49000, 49999, 'SE'], [50000, 56999, 'PE'], [57000, 57999, 'AL'], [58000, 58999, 'PB'], [59000, 59999, 'RN'],
                  [60000, 63999, 'CE'], [64000, 64999, 'PI'], [65000, 65999, 'MA'], [66000, 68899, 'PA'], [68900, 68999, 'AP'],
                  [69000, 69299, 'AM'], [69300, 69399, 'RR'], [69400, 69899, 'AM'], [69900, 69999, 'AC'], [70000, 72799, 'DF'],
                  [72800, 72999, 'GO'], [73000, 73699, 'DF'], [73700, 76799, 'GO'], [76800, 76999, 'RO'], [77000, 77999, 'TO'],
                  [78000, 78899, 'MT'], [79000, 79999, 'MS'], [80000, 87999, 'PR'], [88000, 89999, 'SC'], [90000, 99999, 'RS']]
    uf = faixas.find { cep >= it[0] && cep <= it[1] }?.getAt(2)
}
def pequenas = ['de', 'da', 'do', 'das', 'dos', 'e']
def cidade = addr?.town ? addr.town.trim().toLowerCase().split(/\s+/).collect { w -> w in pequenas ? w : w.capitalize() }.join(' ') : null

// ---- promoções que valeram no pedido (com o desconto de cada uma)
def promocoes = []
(o.allPromotionResults ?: []).findAll { it.certainty == null || it.certainty >= 1.0f }.each { pr ->
    def p = pr.promotion
    if (p == null) return
    def nome = null
    def q = new FlexibleSearchQuery('SELECT {pk} FROM {PromotionSourceRule} WHERE {code}=?c')
    q.addQueryParameter('c', p.code)
    def rule = fs.search(q).result.find { true }
    if (rule) nome = rule.getName(pt) ?: rule.getName()
    def valor = 0d
    def itens = [:].withDefault { 0d }
    (pr.actions ?: []).each { a ->
        def v = (a.hasProperty('amount') && a.amount != null) ? a.amount.doubleValue() : 0d
        valor += v
        // desconto no item (ingresso de sócio, % na loja): guarda o produto para o recorte por área
        def prod = a.hasProperty('orderEntryProduct') ? a.orderEntryProduct?.code : null
        if (prod) itens[prod] += v
    }
    promocoes << [codigo: p.code, nome: nome ?: (p.description ?: p.code), desconto: Math.round(valor * 100) / 100.0d,
                  itens: itens.collect { k, v -> [produto: k, desconto: Math.round(v * 100) / 100.0d] }]
}

// ---- antifraude (ClearSale e verificação de fraude do Commerce)
def cs = get(o, 'clearSaleStatus')
def csNomes = [APA: 'Aprovado automaticamente', APM: 'Aprovado manualmente', APP: 'Aprovado por política',
               RPA: 'Reprovado automaticamente (suspeita de fraude)', RPP: 'Reprovado por política', RPM: 'Reprovado sem suspeita',
               SUS: 'Suspenso por suspeita de fraude', FRD: 'Fraude confirmada', AMA: 'Em análise manual', NVO: 'Aguardando análise',
               CAN: 'Cancelado pelo cliente', ERR: 'Erro na análise']
def suspeita = (cs in ['RPA', 'SUS', 'FRD']) || o.fraudulent == Boolean.TRUE
def recusado = cs in ['RPA', 'RPP', 'RPM', 'SUS', 'FRD']
def antifraude = cs ? [status: cs, resultado: csNomes[cs] ?: cs, score: get(o, 'clearSaleScore'), recusado: recusado, suspeita: suspeita]
        : [status: null, resultado: (o.fraudulent ? 'Fraude (Commerce)' : o.potentiallyFraudulent ? 'Possível fraude (Commerce)' : 'Não analisado pela ClearSale'),
           recusado: false, suspeita: suspeita]
if (o.potentiallyFraudulent) antifraude.possivelFraudeCommerce = true

// ---- perfil do cliente hoje (plano de sócio atual)
def plano = null
def grupos = o.user?.groups ?: []
if (grupos) {
    fs.search(new FlexibleSearchQuery('SELECT {pk} FROM {SocioPlan}')).result.each { sp ->
        def g = get(sp, 'customerGroup')
        if (plano == null && g != null && grupos.contains(g)) plano = sp.getName(pt) ?: sp.getName()
    }
}

return JsonOutput.toJson([
    formaPagamento: forma, bandeira: cartao ? pi.type?.code : null, parcelas: parcelas, recorrencia: recorrencia, gateway: gateway,
    entrega: o.deliveryMode?.code, entregaNome: o.deliveryMode?.getName(pt), lojaRetirada: pos ? (pos.displayName ?: pos.name) : null,
    cidade: cidade, uf: uf,
    promocoes: promocoes, antifraude: antifraude,
    canal: o.salesApplication?.code, planoAtualDoCliente: plano,
])
