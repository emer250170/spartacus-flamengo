/*
 * ClearSale (07/11): nova versão do order-process com o passo clearSaleCheck entre checkOrder e fraudCheck.
 *
 * O passo chama o clearSaleService (extensão flamengomatchday):
 * - OK: segue para o fraudCheck de sempre (aprovado pelo ClearSale, análise desligada ou ClearSale sem resposta);
 * - RECUSADO: o serviço já cancelou o pedido sem cobrança, devolveu estoque e poltrona, cancelou ingressos e
 *   vouchers e disparou o e-mail "Pedido cancelado"; o processo termina em "failed", como o fraudCheck recusado.
 * Sem o bean (antes do ant all) o passo só segue (OK).
 *
 * HAC › Console › Scripting (Groovy). MODO 'conferir' (commit=false) mostra o que muda; 'aplicar' (commit=true)
 * grava a versão nova (o Commerce cria a versão ao salvar o conteúdo) e guarda a anterior em /backup.
 * Para voltar: ativar a versão anterior em Backoffice › Sistema › Processos de negócio › Definições de processo
 * dinâmicas (código order-process).
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def MODO = 'conferir'
// o passo vai em base64: o HAC tira do texto enviado as tags de script do HTML (proteção contra XSS)
def PASSO = new String('PCEtLSBDbGVhclNhbGUgKDA3LzExKTogYW7DoWxpc2UgZGUgZnJhdWRlLCBSZWFsdGltZSBEZWNpc2lvbiAoYWJhIENsZWFyU2FsZSBkYSBsb2phIGJhc2UpIC0tPgo8c2NyaXB0QWN0aW9uIGlkPSdjbGVhclNhbGVDaGVjayc+CiAgICA8c2NyaXB0IHR5cGU9J2dyb292eSc+PCFbQ0RBVEFbCmRlZiBsb2cgPSBvcmcuc2xmNGouTG9nZ2VyRmFjdG9yeS5nZXRMb2dnZXIoJ2ZsYW1lbmdvLWNsZWFyc2FsZScpCmRlZiBjdHggPSBkZS5oeWJyaXMucGxhdGZvcm0uY29yZS5SZWdpc3RyeS5hcHBsaWNhdGlvbkNvbnRleHQKaWYgKCFjdHguY29udGFpbnNCZWFuKCdjbGVhclNhbGVTZXJ2aWNlJykpIHsKICByZXR1cm4gJ09LJwp9CnRyeSB7CiAgcmV0dXJuIGN0eC5nZXRCZWFuKCdjbGVhclNhbGVTZXJ2aWNlJykuYW5hbGlzYXIocHJvY2VzcykKfSBjYXRjaCAoRXhjZXB0aW9uIGV4KSB7CiAgbG9nLmVycm9yKCdQZWRpZG8gJyArIHByb2Nlc3Mub3JkZXI/LmNvZGUgKyAnOiBmYWxoYSBuYSBhbsOhbGlzZSBkbyBDbGVhclNhbGU7IG8gcGVkaWRvIHNlZ3VlJywgZXgpCiAgcmV0dXJuICdPSycKfQpdXT48L3NjcmlwdD4KICAgIDx0cmFuc2l0aW9uIG5hbWU9J09LJyB0bz0nZnJhdWRDaGVjaycvPgogICAgPHRyYW5zaXRpb24gbmFtZT0nUkVDVVNBRE8nIHRvPSdmYWlsZWQnLz4KPC9zY3JpcHRBY3Rpb24+Cg=='.decodeBase64(), 'UTF-8')
def DE = "<action id='checkOrder' bean='checkOrderAction'>\n    <transition name='OK' to='fraudCheck'/>"
def PARA = "<action id='checkOrder' bean='checkOrderAction'>\n    <transition name='OK' to='clearSaleCheck'/>"
def ANTES_DE = '<!-- Fraud Check -->'

def proc = flexibleSearchService.search(new FlexibleSearchQuery(
    "SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")).result[0]
if (proc.content.contains("id='clearSaleCheck'")) {
  return "order-process: a versão ativa (${proc.version}) já tem o passo clearSaleCheck"
}
def conteudo = proc.content.replace('\r\n', '\n')
if (!conteudo.contains(DE) || !conteudo.contains(ANTES_DE)) {
  throw new IllegalStateException('checkOrder -> fraudCheck não encontrado na versão ativa ' + proc.version)
}
def novo = conteudo.replace(DE, PARA)
def k = novo.indexOf(ANTES_DE)
novo = novo.substring(0, k) + PASSO.trim() + '\n\n' + novo.substring(k)
if (MODO != 'aplicar') {
  return "conferir: versão ativa ${proc.version}; a nova versão manda checkOrder -> clearSaleCheck -> fraudCheck " +
      "(RECUSADO -> failed). Conteúdo: ${conteudo.length()} -> ${novo.length()} caracteres."
}
def bk = new File('/backup'); bk.mkdirs()
def f = new File(bk, "order-process-v${proc.version}-antes-clearsale.xml")
f.setText(proc.content, 'UTF-8')
def anterior = proc.version
proc.content = novo
modelService.save(proc)
def ativo = flexibleSearchService.search(new FlexibleSearchQuery(
    "SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")).result[0]
return "order-process: versão ${anterior} -> ${ativo.version} (ativa), com o passo clearSaleCheck. Backup: ${f.path}"
