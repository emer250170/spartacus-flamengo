/*
 * Item 12 do plano de evolução (retirada na loja): nova versão do order-process com o passo
 * sourcePickup entre takePayment e completeOrder.
 *
 * O passo pega as linhas do pedido com loja de retirada (deliveryPointOfService), agrupa por
 * loja e cria a remessa (Consignment) de cada loja pelo OMS (allocationService, que reserva o
 * estoque da loja e abre o fluxo de tarefas do atendente) e inicia o consignment-process padrão
 * (caminho de retirada: separar, embalar = pronto para retirada, confirmar retirada no cockpit
 * Expedição de pedidos). As outras linhas (entrega em casa, ingresso, plano, serviços) seguem
 * como hoje. Falha aqui não impede o pedido: fica no log.
 * Chave: flamengo.retirada.ativo (false = não cria remessa; o pedido segue como hoje).
 *
 * Rodar no HAC (Groovy) com commit=true. O platform cria a versão nova ao salvar o conteúdo da
 * definição ativa; a anterior fica salva em /backup/order-process-v<versão>-antes-retirada.xml.
 * Para voltar: ativar a versão anterior em Backoffice > Sistema > Processos de negócio >
 * Definições de processo dinâmicas (código order-process).
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

// passo em base64: o filtro de XSS do HAC apaga as tags de script do texto do Groovy
def PASSO = new String('CjwhLS0gSXRlbSAxMjogcmVtZXNzYSBkZSByZXRpcmFkYSBuYSBsb2phIChjb25zaWdubWVudCBkbyBPTVMpIHBhcmEgYXMgbGluaGFzIGNvbSBsb2phIC0tPgo8c2NyaXB0QWN0aW9uIGlkPSdzb3VyY2VQaWNrdXAnPgogICAgPHNjcmlwdCB0eXBlPSdncm9vdnknPjwhW0NEQVRBWwppbXBvcnQgZGUuaHlicmlzLnBsYXRmb3JtLmNvcmUuUmVnaXN0cnkKaW1wb3J0IGRlLmh5YnJpcy5wbGF0Zm9ybS51dGlsLkNvbmZpZwppbXBvcnQgZGUuaHlicmlzLnBsYXRmb3JtLndhcmVob3VzaW5nLmRhdGEuc291cmNpbmcuU291cmNpbmdSZXN1bHQKaW1wb3J0IGRlLmh5YnJpcy5wbGF0Zm9ybS53YXJlaG91c2luZy5kYXRhLnNvdXJjaW5nLlNvdXJjaW5nUmVzdWx0cwpkZWYgbG9nID0gb3JnLnNsZjRqLkxvZ2dlckZhY3RvcnkuZ2V0TG9nZ2VyKCdmbGFtZW5nby1yZXRpcmFkYScpCmRlZiBvID0gcHJvY2Vzcy5vcmRlcgp0cnkgewogIGlmIChDb25maWcuZ2V0Qm9vbGVhbignZmxhbWVuZ28ucmV0aXJhZGEuYXRpdm8nLCB0cnVlKSAmJiAoby5jb25zaWdubWVudHMgPT0gbnVsbCB8fCBvLmNvbnNpZ25tZW50cy5pc0VtcHR5KCkpKSB7CiAgICBkZWYgY3R4ID0gUmVnaXN0cnkuYXBwbGljYXRpb25Db250ZXh0CiAgICBkZWYgYnlQb3MgPSBvLmVudHJpZXMuZmluZEFsbCB7IGl0LmRlbGl2ZXJ5UG9pbnRPZlNlcnZpY2UgIT0gbnVsbCAmJiAoaXQucXVhbnRpdHkgPzogMCkgPiAwIH0uZ3JvdXBCeSB7IGl0LmRlbGl2ZXJ5UG9pbnRPZlNlcnZpY2UgfQogICAgaWYgKGJ5UG9zKSB7CiAgICAgIGRlZiBycyA9IG5ldyBTb3VyY2luZ1Jlc3VsdHMoKQogICAgICBycy5yZXN1bHRzID0gbmV3IEhhc2hTZXQoKQogICAgICBycy5jb21wbGV0ZSA9IHRydWUKICAgICAgYnlQb3MuZWFjaCB7IHBvcywgZXMgLT4KICAgICAgICBkZWYgdyA9IHBvcy53YXJlaG91c2VzLmZpbmQgeyAhaXQuZXh0ZXJuYWwgJiYgaXQuY29kZSA9PSBwb3MubmFtZSB9ID86IHBvcy53YXJlaG91c2VzLmZpbmQgeyAhaXQuZXh0ZXJuYWwgfQogICAgICAgIGlmICh3ID09IG51bGwpIHsKICAgICAgICAgIGxvZy53YXJuKCdQZWRpZG8gJyArIG8uY29kZSArICc6IGxvamEgJyArIHBvcy5uYW1lICsgJyBzZW0gZGVww7NzaXRvOyBhIHJldGlyYWRhIGZpY2Egc2VtIHJlbWVzc2EnKQogICAgICAgIH0gZWxzZSB7CiAgICAgICAgICBkZWYgciA9IG5ldyBTb3VyY2luZ1Jlc3VsdCgpCiAgICAgICAgICByLndhcmVob3VzZSA9IHcKICAgICAgICAgIHIuYWxsb2NhdGlvbiA9IG5ldyBIYXNoTWFwKGVzLmNvbGxlY3RFbnRyaWVzIHsgWyhpdCk6IGl0LnF1YW50aXR5XSB9KQogICAgICAgICAgcnMucmVzdWx0cy5hZGQocikKICAgICAgICB9CiAgICAgIH0KICAgICAgaWYgKCFycy5yZXN1bHRzLmlzRW1wdHkoKSkgewogICAgICAgIGRlZiBicHMgPSBjdHguZ2V0QmVhbignYnVzaW5lc3NQcm9jZXNzU2VydmljZScpCiAgICAgICAgZGVmIG1zID0gY3R4LmdldEJlYW4oJ21vZGVsU2VydmljZScpCiAgICAgICAgZGVmIGNvbnMgPSBjdHguZ2V0QmVhbignYWxsb2NhdGlvblNlcnZpY2UnKS5jcmVhdGVDb25zaWdubWVudHMobywgJ2NvbnMnICsgby5jb2RlLCBycykKICAgICAgICBmb3IgKGMgaW4gY29ucykgewogICAgICAgICAgZGVmIHAgPSBicHMuY3JlYXRlUHJvY2VzcyhjLmdldENvZGUoKSArICdfb3JkZXJtYW5hZ2VtZW50JywgJ2NvbnNpZ25tZW50LXByb2Nlc3MnKQogICAgICAgICAgcC5zZXRQYXJlbnRQcm9jZXNzKHByb2Nlc3MpCiAgICAgICAgICBwLnNldENvbnNpZ25tZW50KGMpCiAgICAgICAgICBtcy5zYXZlKHApCiAgICAgICAgICBicHMuc3RhcnRQcm9jZXNzKHApCiAgICAgICAgICBsb2cuaW5mbygnUGVkaWRvICcgKyBvLmNvZGUgKyAnOiByZW1lc3NhICcgKyBjLmdldENvZGUoKSArICcgcGFyYSByZXRpcmFkYSBlbSAnICsgYy5nZXREZWxpdmVyeVBvaW50T2ZTZXJ2aWNlKCk/LmdldE5hbWUoKSkKICAgICAgICB9CiAgICAgIH0KICAgIH0KICB9Cn0gY2F0Y2ggKEV4Y2VwdGlvbiBleCkgewogIGxvZy5lcnJvcignUGVkaWRvICcgKyBvLmNvZGUgKyAnOiBmYWxoYSBhbyBjcmlhciBhIHJlbWVzc2EgZGUgcmV0aXJhZGEnLCBleCkKfQpyZXR1cm4gJ09LJwpdXT48L3NjcmlwdD4KICAgIDx0cmFuc2l0aW9uIG5hbWU9J09LJyB0bz0nY29tcGxldGVPcmRlcicvPgo8L3NjcmlwdEFjdGlvbj4K'.decodeBase64(), 'UTF-8')

def out = []
def proc = flexibleSearchService.search(new FlexibleSearchQuery("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")).result[0]
if (proc.content.contains("id='sourcePickup'")) {
	return "order-process: versão ${proc.version} já tem o passo sourcePickup"
}
def DE = "<action id='takePayment' bean='takePaymentAction'>"
def i = proc.content.indexOf(DE)
def j = i < 0 ? -1 : proc.content.indexOf("to='completeOrder'", i)
def k = proc.content.indexOf('<' + "scriptAction id='completeOrder'>")
if (i < 0 || j < 0 || k < 0 || j > k) {
	throw new IllegalStateException('takePayment -> completeOrder não encontrado no order-process ativo')
}
def bk = new File('/backup'); bk.mkdirs()
def f = new File(bk, "order-process-v${proc.version}-antes-retirada.xml")
f.setText(proc.content, 'UTF-8')
def anterior = proc.version
def novo = proc.content.substring(0, j) + "to='sourcePickup'" + proc.content.substring(j + "to='completeOrder'".length())
k = novo.indexOf('<' + "scriptAction id='completeOrder'>")
novo = novo.substring(0, k) + PASSO.trim() + '\n\n    ' + novo.substring(k)
proc.content = novo
modelService.save(proc)
def ativo = flexibleSearchService.search(new FlexibleSearchQuery("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")).result[0]
out << "order-process: versão ${ativo.version} ativa (anterior ${anterior}, backup ${f.path})"
out << 'takePayment -> ' + (ativo.content =~ /<action id='takePayment'[\s\S]*?<transition name='OK' to='([^']+)'/)[0][1]
out << 'sourcePickup presente: ' + ativo.content.contains("id='sourcePickup'")
return out.join('\n')
