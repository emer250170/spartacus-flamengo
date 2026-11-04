/*
 * Deploy da extensão flamengomatchday pelo HAC (Console › Scripting, Groovy).
 * Gerado por backend/hac-deploy/gerar.py — não editar à mão.
 *
 * MODO 'conferir': só compara o md5 dos arquivos no servidor com o esperado. Não grava nada.
 * MODO 'aplicar' : confere, faz backup da extensão inteira em /backup, grava os arquivos
 *                  novos, apaga os removidos e confere o md5 final. Se o md5 inicial não
 *                  bater, não grava nada.
 * Depois de 'aplicar': parar o servidor, ant all, subir (feito pelo Emerson).
 */
import java.security.MessageDigest

def MODO = '@@MODO@@'
def ROTULO = '@@ROTULO@@'
def EXT = new File('/opt/sap/hybris/bin/custom/flamengomatchday')
// caminho relativo -> md5 esperado ANTES ('NOVO' = não pode existir)
def ANTES = @@ANTES@@
// caminho relativo -> md5 DEPOIS ('REMOVIDO' = deve sumir)
def DEPOIS = @@DEPOIS@@
// tar.gz (base64) só com os arquivos novos/alterados, caminhos relativos à extensão
// em partes: o Groovy não aceita texto literal com mais de 65535 caracteres (PR #124)
def PACOTE = new StringBuilder()@@PACOTE@@.toString()

def md5 = { File f -> f.exists() ? MessageDigest.getInstance('MD5').digest(f.bytes).encodeHex().toString() : 'NAO_EXISTE' }
def sh = { List cmd -> def p = new ProcessBuilder(cmd).redirectErrorStream(true).start(); def o = p.inputStream.text; p.waitFor(); [p.exitValue(), o.trim()] }
def out = new StringBuilder("${ROTULO} · modo ${MODO}\n")

def divergentes = []
ANTES.each { rel, esperado ->
  def atual = md5(new File(EXT, rel))
  def ok = (esperado == 'NOVO') ? atual == 'NAO_EXISTE' : atual == esperado
  if (!ok) divergentes << "${rel}: servidor=${atual} esperado=${esperado}"
}
out << "Conferência inicial: ${ANTES.size() - divergentes.size()}/${ANTES.size()} ok\n"
divergentes.each { out << "  DIVERGE ${it}\n" }
if (MODO != 'aplicar') return out.toString()
if (divergentes) return out << 'NADA FOI GRAVADO: o servidor não está na versão esperada.\n' as String

def ts = new java.text.SimpleDateFormat('yyyyMMdd-HHmmss').format(new Date())
def bkp = new File("/backup/flamengomatchday-antes-${ROTULO}-${ts}")
bkp.mkdirs()
def (rc1, o1) = sh(['tar', 'czf', new File(bkp, 'fontes.tgz').path, '-C', EXT.parent, EXT.name])
out << "Backup: ${bkp}/fontes.tgz (rc=${rc1}) ${o1}\n"
if (rc1 != 0) return out << 'NADA FOI GRAVADO: backup falhou.\n' as String

if (PACOTE) {
  def tgz = File.createTempFile('deploy-', '.tgz')
  tgz.bytes = PACOTE.decodeBase64()
  // -m: data de agora nos arquivos gravados, para o ant all recompilar
  def (rc2, o2) = sh(['tar', 'xzmf', tgz.path, '-C', EXT.path])
  tgz.delete()
  out << "Arquivos gravados (rc=${rc2}) ${o2}\n"
}
DEPOIS.findAll { it.value == 'REMOVIDO' }.each { rel, v -> out << "Removido ${rel}: ${new File(EXT, rel).delete()}\n" }

def ruins = DEPOIS.findAll { rel, esperado -> esperado == 'REMOVIDO' ? new File(EXT, rel).exists() : md5(new File(EXT, rel)) != esperado }
out << "Conferência final: ${DEPOIS.size() - ruins.size()}/${DEPOIS.size()} ok\n"
ruins.each { rel, v -> out << "  ERRO ${rel}: servidor=${md5(new File(EXT, rel))} esperado=${v}\n" }
out << (ruins ? "ATENÇÃO: restaure com tar xzf ${bkp}/fontes.tgz -C ${EXT.parent}\n" : 'OK. Falta: parar o servidor, ant all, subir.\n')
return out.toString()
