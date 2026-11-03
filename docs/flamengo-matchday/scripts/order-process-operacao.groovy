/*
 * Item 14 do plano de evolução (operação do pedido da loja): instalador pelo HAC.
 * GERADO por gerar-order-process-operacao.py a partir de order-process-operacao.xml: não editar
 * o .groovy à mão (editar o .xml ou o .tpl e gerar de novo).
 *
 * ETAPA:
 *  'conferir'  : confere que a versão ativa do order-process é a v6 do item 12 (md5), monta a versão
 *                nova, confere o XML e as transições e compila os scripts dos passos. Não grava nada.
 *  'aplicar'   : o mesmo, mais backup em /backup/order-process-v<versão>-antes-operacao.xml e a
 *                gravação (o platform cria a versão nova e a ativa).
 *  'atendente' : atendente do centro de distribuição (atendente.cd, sem senha: o Emerson define no
 *                Backoffice) no grupo da loja online (depósito warehouse_s) e nos grupos do armazém.
 *  'traducoes' : nomes em português dos status do pedido usados no item 14 (o inglês não muda).
 *
 * A versão nova só muda o pedido com a chave flamengo.loja.operacao.ativo=true; desligada, o pedido
 * anda como antes. Para voltar de vez: ativar a versão anterior em Backoffice > Sistema > Processos
 * de negócio > Definições de processo dinâmicas (código order-process).
 */
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.commerceservices.model.user.StoreEmployeeGroupModel
import de.hybris.platform.core.model.user.EmployeeModel

def ETAPA = 'conferir'
def BASE_MD5 = '418f1985546d3e9d62d76fca1858fa68'   // order-process v6 (item 12)
def PAGAMENTO = new String('PCEtLSBJdGVtIDE0OiBQaXggb3UgYm9sZXRvIGVzcGVyYSBhIGNvbmZpcm1hw6fDo28gKHN0YXR1cyBkbyBwZWRpZG8gIlBhZ2FtZW50byBjb25maXJtYWRvIikgLS0+CjxzY3JpcHRBY3Rpb24gaWQ9J2NoZWNrUGF5bWVudCc+CiAgICA8c2NyaXB0IHR5cGU9J2dyb292eSc+PCFbQ0RBVEFbCmltcG9ydCBkZS5oeWJyaXMucGxhdGZvcm0uY29yZS5SZWdpc3RyeQppbXBvcnQgZGUuaHlicmlzLnBsYXRmb3JtLmNvcmUuZW51bXMuT3JkZXJTdGF0dXMKaW1wb3J0IGRlLmh5YnJpcy5wbGF0Zm9ybS5zZXJ2aWNlbGF5ZXIuc2VhcmNoLkZsZXhpYmxlU2VhcmNoUXVlcnkKaW1wb3J0IGRlLmh5YnJpcy5wbGF0Zm9ybS5jb3JlLm1vZGVsLnVzZXIuQ3VzdG9tZXJNb2RlbAppbXBvcnQgYnIuY29tLmZsYW1lbmdvLm1hdGNoZGF5LmVudW1zLlNvY2lvRXZlbnRUeXBlCmltcG9ydCBici5jb20uZmxhbWVuZ28ubWF0Y2hkYXkuc29jaW8uU29jaW9QYXltZW50cwpkZWYgbG9nID0gb3JnLnNsZjRqLkxvZ2dlckZhY3RvcnkuZ2V0TG9nZ2VyKCdmbGFtZW5nby1wYWdhbWVudG8nKQpkZWYgY3R4ID0gUmVnaXN0cnkuYXBwbGljYXRpb25Db250ZXh0CmRlZiBtcyA9IGN0eC5nZXRCZWFuKCdtb2RlbFNlcnZpY2UnKQpkZWYgbyA9IHByb2Nlc3Mub3JkZXIKbXMucmVmcmVzaChvKQppZiAoby5zdGF0dXMgPT0gT3JkZXJTdGF0dXMuQ0FOQ0VMTEVEKSB7CiAgbG9nLmluZm8oJ1BlZGlkbyAnICsgby5jb2RlICsgJzogY2FuY2VsYWRvIGVucXVhbnRvIGFndWFyZGF2YSBvIHBhZ2FtZW50bycpCiAgcmV0dXJuICdDQU5DRUxMRUQnCn0KZGVmIHBheSA9IGN0eC5nZXRCZWFuKCdzb2Npb1BheW1lbnRTZXJ2aWNlJykKaWYgKCFwYXkuaXNTdG9yZUVuYWJsZWQoKSB8fCAhU29jaW9QYXltZW50cy51cGZyb250KFNvY2lvUGF5bWVudHMubWV0aG9kKG1zLCBvKSkpIHsKICByZXR1cm4gJ09LJwp9CmRlZiBwbGFucyA9IGN0eC5nZXRCZWFuKCdzb2Npb1BsYW5TZXJ2aWNlJykKaWYgKFNvY2lvUGF5bWVudHMuaXNBd2FpdGluZyhtcywgbykpIHsKICBpZiAoby5zdGF0dXMgIT0gT3JkZXJTdGF0dXMuUEFZTUVOVF9OT1RfQ0FQVFVSRUQgJiYgby5zdGF0dXMgIT0gT3JkZXJTdGF0dXMuUEFZTUVOVF9DQVBUVVJFRCkgewogICAgby5zdGF0dXMgPSBPcmRlclN0YXR1cy5QQVlNRU5UX05PVF9DQVBUVVJFRAogICAgbXMuc2F2ZShvKQogICAgLy8gcGxhbm8gbm8gcGVkaWRvOiBvIHRvcmNlZG9yIGZpY2EgImFndWFyZGFuZG8gcGFnYW1lbnRvIiBuYSBmaWNoYSBkbyBzw7NjaW8sIGNvbW8gbm8gaXRlbSA3CiAgICB0cnkgeyBwbGFucy5hcHBseU9yZGVyKG8pIH0gY2F0Y2ggKEV4Y2VwdGlvbiBleCkgeyBsb2cuZXJyb3IoJ1BlZGlkbyAnICsgby5jb2RlICsgJzogcGxhbm8gYWd1YXJkYW5kbyBwYWdhbWVudG8nLCBleCkgfQogICAgbG9nLmluZm8oJ1BlZGlkbyAnICsgby5jb2RlICsgJzogYWd1YXJkYW5kbyBwYWdhbWVudG8gKCcgKyBTb2Npb1BheW1lbnRzLmRlc2NyaWJlKG1zLCBvKSArICcpJykKICB9CiAgcmV0dXJuICdXQUlUJwp9Ci8vIHBhZ286IG8gcGVkaWRvIGNvbSBwbGFubyBnYW5oYSBvIGV2ZW50byAiUGFnYW1lbnRvIGNvbmZpcm1hZG8iIG5hIGxpbmhhIGRvIHRlbXBvIGRvIHPDs2NpbwovLyAoc2UgYSBjb25maXJtYcOnw6NvIHZlaW8gZGEgZmljaGEgb3UgZG8gQ2xhdWRlLCBvIGV2ZW50byBqw6EgZXhpc3RlKQp0cnkgewogIGRlZiBjb21QbGFubyA9IG8uZW50cmllcy5hbnkgeyBwbGFucy5maW5kRm9yUHJvZHVjdChpdC5wcm9kdWN0KSAhPSBudWxsIH0KICBpZiAoY29tUGxhbm8gJiYgby51c2VyIGluc3RhbmNlb2YgQ3VzdG9tZXJNb2RlbCkgewogICAgZGVmIHEgPSBuZXcgRmxleGlibGVTZWFyY2hRdWVyeSgiU0VMRUNUIHtwa30gRlJPTSB7U29jaW9FdmVudH0gV0hFUkUge2N1c3RvbWVyfT0/YyBBTkQge3R5cGV9PT90IEFORCAoe25vdGV9IExJS0UgP3AgT1Ige3Jlc3VsdH0gTElLRSA/cCkiLAogICAgICAgIFtjOiBvLnVzZXIsIHQ6IFNvY2lvRXZlbnRUeXBlLlBBR0FNRU5UT19DT05GSVJNQURPLCBwOiAnJScgKyBvLmNvZGUgKyAnJSddKQogICAgaWYgKGN0eC5nZXRCZWFuKCdmbGV4aWJsZVNlYXJjaFNlcnZpY2UnKS5zZWFyY2gocSkudG90YWxDb3VudCA9PSAwKSB7CiAgICAgIGRlZiBldiA9IHBsYW5zLm5ld0V2ZW50KG8udXNlciwgU29jaW9FdmVudFR5cGUuUEFHQU1FTlRPX0NPTkZJUk1BRE8sICdCYWNrb2ZmaWNlJywKICAgICAgICAgICdQYWdhbWVudG8gZG8gcGVkaWRvICcgKyBvLmNvZGUgKyAnIGNvbmZpcm1hZG8gcGVsYSBtdWRhbsOnYSBkZSBzdGF0dXMgZG8gcGVkaWRvJykKICAgICAgbXMuc2F2ZShldikKICAgIH0KICB9Cn0gY2F0Y2ggKEV4Y2VwdGlvbiBleCkgewogIGxvZy5lcnJvcignUGVkaWRvICcgKyBvLmNvZGUgKyAnOiBldmVudG8gZGUgcGFnYW1lbnRvIGNvbmZpcm1hZG8nLCBleCkKfQpsb2cuaW5mbygnUGVkaWRvICcgKyBvLmNvZGUgKyAnOiBwYWdhbWVudG8gY29uZmlybWFkbywgbyBwZWRpZG8gc2VndWUnKQpyZXR1cm4gJ09LJwpdXT48L3NjcmlwdD4KICAgIDx0cmFuc2l0aW9uIG5hbWU9J09LJyB0bz0nc291cmNlRnVsZmlsbG1lbnQnLz4KICAgIDx0cmFuc2l0aW9uIG5hbWU9J1dBSVQnIHRvPSd3YWl0Rm9yUGF5bWVudCcvPgogICAgPHRyYW5zaXRpb24gbmFtZT0nQ0FOQ0VMTEVEJyB0bz0nc3VjY2VzcycvPgo8L3NjcmlwdEFjdGlvbj4KCjwhLS0gY29uZmVyZSBkZSBub3ZvIGEgY2FkYSBtaW51dG8gb3UgcXVhbmRvIG8gZXZlbnRvIFBhZ2FtZW50b0NvbmZpcm1hZG8gY2hlZ2FyIC0tPgo8d2FpdCBpZD0nd2FpdEZvclBheW1lbnQnIHRoZW49J2NoZWNrUGF5bWVudCcgcHJlcGVuZFByb2Nlc3NDb2RlPSd0cnVlJz4KICAgIDxldmVudD5QYWdhbWVudG9Db25maXJtYWRvPC9ldmVudD4KICAgIDx0aW1lb3V0IGRlbGF5PSdQVDFNJyB0aGVuPSdjaGVja1BheW1lbnQnLz4KPC93YWl0PgoKPCEtLSBJdGVucyAxMiBlIDE0OiByZW1lc3NhcyBkbyBPTVMuIFJldGlyYWRhIG5hIGxvamEgKGNvbW8gbm8gaXRlbSAxMikgZSBlbnRyZWdhIGVtIGNhc2EKICAgICAocHJvZHV0b3MgZsOtc2ljb3MsIGRlcMOzc2l0byB3YXJlaG91c2VfcykgLS0+CjxzY3JpcHRBY3Rpb24gaWQ9J3NvdXJjZUZ1bGZpbGxtZW50Jz4KICAgIDxzY3JpcHQgdHlwZT0nZ3Jvb3Z5Jz48IVtDREFUQVsKaW1wb3J0IGRlLmh5YnJpcy5wbGF0Zm9ybS5jb3JlLlJlZ2lzdHJ5CmltcG9ydCBkZS5oeWJyaXMucGxhdGZvcm0udXRpbC5Db25maWcKaW1wb3J0IGRlLmh5YnJpcy5wbGF0Zm9ybS5zZXJ2aWNlbGF5ZXIuc2VhcmNoLkZsZXhpYmxlU2VhcmNoUXVlcnkKaW1wb3J0IGRlLmh5YnJpcy5wbGF0Zm9ybS5jb21tZXJjZXNlcnZpY2VzLm1vZGVsLlBpY2tVcERlbGl2ZXJ5TW9kZU1vZGVsCmltcG9ydCBkZS5oeWJyaXMucGxhdGZvcm0ud2FyZWhvdXNpbmcuZGF0YS5zb3VyY2luZy5Tb3VyY2luZ1Jlc3VsdAppbXBvcnQgZGUuaHlicmlzLnBsYXRmb3JtLndhcmVob3VzaW5nLmRhdGEuc291cmNpbmcuU291cmNpbmdSZXN1bHRzCmRlZiBsb2cgPSBvcmcuc2xmNGouTG9nZ2VyRmFjdG9yeS5nZXRMb2dnZXIoJ2ZsYW1lbmdvLWV4cGVkaWNhbycpCmRlZiBvID0gcHJvY2Vzcy5vcmRlcgp0cnkgewogIGlmIChvLmNvbnNpZ25tZW50cyA9PSBudWxsIHx8IG8uY29uc2lnbm1lbnRzLmlzRW1wdHkoKSkgewogICAgZGVmIGN0eCA9IFJlZ2lzdHJ5LmFwcGxpY2F0aW9uQ29udGV4dAogICAgZGVmIHJzID0gbmV3IFNvdXJjaW5nUmVzdWx0cygpCiAgICBycy5yZXN1bHRzID0gbmV3IEhhc2hTZXQoKQogICAgcnMuY29tcGxldGUgPSB0cnVlCiAgICAvLyByZXRpcmFkYSBuYSBsb2phIChpdGVtIDEyKQogICAgaWYgKENvbmZpZy5nZXRCb29sZWFuKCdmbGFtZW5nby5yZXRpcmFkYS5hdGl2bycsIHRydWUpKSB7CiAgICAgIGRlZiBieVBvcyA9IG8uZW50cmllcy5maW5kQWxsIHsgaXQuZGVsaXZlcnlQb2ludE9mU2VydmljZSAhPSBudWxsICYmIChpdC5xdWFudGl0eSA/OiAwKSA+IDAgfS5ncm91cEJ5IHsgaXQuZGVsaXZlcnlQb2ludE9mU2VydmljZSB9CiAgICAgIGJ5UG9zLmVhY2ggeyBwb3MsIGVzIC0+CiAgICAgICAgZGVmIHcgPSBwb3Mud2FyZWhvdXNlcy5maW5kIHsgIWl0LmV4dGVybmFsICYmIGl0LmNvZGUgPT0gcG9zLm5hbWUgfSA/OiBwb3Mud2FyZWhvdXNlcy5maW5kIHsgIWl0LmV4dGVybmFsIH0KICAgICAgICBpZiAodyA9PSBudWxsKSB7CiAgICAgICAgICBsb2cud2FybignUGVkaWRvICcgKyBvLmNvZGUgKyAnOiBsb2phICcgKyBwb3MubmFtZSArICcgc2VtIGRlcMOzc2l0bzsgYSByZXRpcmFkYSBmaWNhIHNlbSByZW1lc3NhJykKICAgICAgICB9IGVsc2UgewogICAgICAgICAgZGVmIHIgPSBuZXcgU291cmNpbmdSZXN1bHQoKQogICAgICAgICAgci53YXJlaG91c2UgPSB3CiAgICAgICAgICByLmFsbG9jYXRpb24gPSBuZXcgSGFzaE1hcChlcy5jb2xsZWN0RW50cmllcyB7IFsoaXQpOiBpdC5xdWFudGl0eV0gfSkKICAgICAgICAgIHJzLnJlc3VsdHMuYWRkKHIpCiAgICAgICAgfQogICAgICB9CiAgICB9CiAgICAvLyBlbnRyZWdhIGVtIGNhc2EgKGl0ZW0gMTQpOiBwcm9kdXRvcyBmw61zaWNvcyBjb20gZXN0b3F1ZSBubyBkZXDDs3NpdG8gZGEgbG9qYSBvbmxpbmUKICAgIGlmIChjdHguZ2V0QmVhbignc29jaW9QYXltZW50U2VydmljZScpLmlzU3RvcmVFbmFibGVkKCkpIHsKICAgICAgZGVmIGRtID0gby5kZWxpdmVyeU1vZGUKICAgICAgaWYgKGRtICE9IG51bGwgJiYgIShkbSBpbnN0YW5jZW9mIFBpY2tVcERlbGl2ZXJ5TW9kZU1vZGVsKSAmJiBkbS5jb2RlICE9ICdtYXRjaGRheS1kaWdpdGFsJykgewogICAgICAgIGRlZiB3ID0gY3R4LmdldEJlYW4oJ3dhcmVob3VzZVNlcnZpY2UnKS5nZXRXYXJlaG91c2VGb3JDb2RlKENvbmZpZy5nZXRTdHJpbmcoJ2ZsYW1lbmdvLmxvamEub3BlcmFjYW8uZGVwb3NpdG8nLCAnd2FyZWhvdXNlX3MnKSkKICAgICAgICBkZWYgc2tpcCA9IENvbmZpZy5nZXRTdHJpbmcoJ2ZsYW1lbmdvLmxvamEub3BlcmFjYW8uc2VtUmVtZXNzYScsICcnKS5zcGxpdCgnLCcpLmNvbGxlY3QgeyBpdC50cmltKCkgfS5maW5kQWxsIHsgaXQgfQogICAgICAgIGRlZiBmcyA9IGN0eC5nZXRCZWFuKCdmbGV4aWJsZVNlYXJjaFNlcnZpY2UnKQogICAgICAgIGRlZiBzaGlwID0gby5lbnRyaWVzLmZpbmRBbGwgeyBlIC0+CiAgICAgICAgICBlLmRlbGl2ZXJ5UG9pbnRPZlNlcnZpY2UgPT0gbnVsbCAmJiAoZS5xdWFudGl0eSA/OiAwKSA+IDAgJiYgIXNraXAuYW55IHsgcCAtPiBlLnByb2R1Y3QuY29kZS5zdGFydHNXaXRoKHApIH0gJiYKICAgICAgICAgICAgICBmcy5zZWFyY2gobmV3IEZsZXhpYmxlU2VhcmNoUXVlcnkoJ1NFTEVDVCB7cGt9IEZST00ge1N0b2NrTGV2ZWx9IFdIRVJFIHtwcm9kdWN0Q29kZX09P2MgQU5EIHt3YXJlaG91c2V9PT93JywKICAgICAgICAgICAgICAgICAgW2M6IGUucHJvZHVjdC5jb2RlLCB3OiB3XSkpLnRvdGFsQ291bnQgPiAwCiAgICAgICAgfQogICAgICAgIGlmIChzaGlwKSB7CiAgICAgICAgICBkZWYgciA9IG5ldyBTb3VyY2luZ1Jlc3VsdCgpCiAgICAgICAgICByLndhcmVob3VzZSA9IHcKICAgICAgICAgIHIuYWxsb2NhdGlvbiA9IG5ldyBIYXNoTWFwKHNoaXAuY29sbGVjdEVudHJpZXMgeyBbKGl0KTogaXQucXVhbnRpdHldIH0pCiAgICAgICAgICBycy5yZXN1bHRzLmFkZChyKQogICAgICAgIH0KICAgICAgfQogICAgfQogICAgaWYgKCFycy5yZXN1bHRzLmlzRW1wdHkoKSkgewogICAgICBkZWYgYnBzID0gY3R4LmdldEJlYW4oJ2J1c2luZXNzUHJvY2Vzc1NlcnZpY2UnKQogICAgICBkZWYgbXMgPSBjdHguZ2V0QmVhbignbW9kZWxTZXJ2aWNlJykKICAgICAgZGVmIGNvbnMgPSBjdHguZ2V0QmVhbignYWxsb2NhdGlvblNlcnZpY2UnKS5jcmVhdGVDb25zaWdubWVudHMobywgJ2NvbnMnICsgby5jb2RlLCBycykKICAgICAgZm9yIChjIGluIGNvbnMpIHsKICAgICAgICBkZWYgcCA9IGJwcy5jcmVhdGVQcm9jZXNzKGMuZ2V0Q29kZSgpICsgJ19vcmRlcm1hbmFnZW1lbnQnLCAnY29uc2lnbm1lbnQtcHJvY2VzcycpCiAgICAgICAgcC5zZXRQYXJlbnRQcm9jZXNzKHByb2Nlc3MpCiAgICAgICAgcC5zZXRDb25zaWdubWVudChjKQogICAgICAgIG1zLnNhdmUocCkKICAgICAgICBicHMuc3RhcnRQcm9jZXNzKHApCiAgICAgICAgbG9nLmluZm8oJ1BlZGlkbyAnICsgby5jb2RlICsgJzogcmVtZXNzYSAnICsgYy5nZXRDb2RlKCkgKyAnIGRvIGRlcMOzc2l0byAnICsgYy5nZXRXYXJlaG91c2UoKT8uZ2V0Q29kZSgpCiAgICAgICAgICAgICsgKGMuZ2V0RGVsaXZlcnlQb2ludE9mU2VydmljZSgpID8gJyBwYXJhIHJldGlyYWRhIGVtICcgKyBjLmdldERlbGl2ZXJ5UG9pbnRPZlNlcnZpY2UoKS5nZXROYW1lKCkgOiAnIHBhcmEgZW50cmVnYScpKQogICAgICB9CiAgICB9CiAgfQp9IGNhdGNoIChFeGNlcHRpb24gZXgpIHsKICBsb2cuZXJyb3IoJ1BlZGlkbyAnICsgby5jb2RlICsgJzogZmFsaGEgYW8gY3JpYXIgYXMgcmVtZXNzYXMnLCBleCkKfQpyZXR1cm4gJ09LJwpdXT48L3NjcmlwdD4KICAgIDx0cmFuc2l0aW9uIG5hbWU9J09LJyB0bz0nY29tcGxldGVPcmRlcicvPgo8L3NjcmlwdEFjdGlvbj4K'.decodeBase64(), 'UTF-8')
def FECHAMENTO = new String('ICAgIDxzY3JpcHRBY3Rpb24gaWQ9J2NvbXBsZXRlT3JkZXInPgogICAgPHNjcmlwdCB0eXBlPSdncm9vdnknPjwhW0NEQVRBWwppbXBvcnQgZGUuaHlicmlzLnBsYXRmb3JtLmNvcmUuUmVnaXN0cnkKaW1wb3J0IGRlLmh5YnJpcy5wbGF0Zm9ybS5jb3JlLmVudW1zLk9yZGVyU3RhdHVzCmltcG9ydCBkZS5oeWJyaXMucGxhdGZvcm0uYmFzZWNvbW1lcmNlLmVudW1zLkNvbnNpZ25tZW50U3RhdHVzCmltcG9ydCBkZS5oeWJyaXMucGxhdGZvcm0uc2VydmljZWxheWVyLnNlYXJjaC5GbGV4aWJsZVNlYXJjaFF1ZXJ5CmRlZiBjdHggPSBSZWdpc3RyeS5hcHBsaWNhdGlvbkNvbnRleHQKZGVmIGZzID0gY3R4LmdldEJlYW4oJ2ZsZXhpYmxlU2VhcmNoU2VydmljZScpCmRlZiBtcyA9IGN0eC5nZXRCZWFuKCdtb2RlbFNlcnZpY2UnKQpkZWYgbyA9IHByb2Nlc3Mub3JkZXIKLy8gSXRlbSAxNDogY29tIGEgY2hhdmUgbGlnYWRhLCBvIGVzdG9xdWUgZGFzIGxpbmhhcyBjb20gcmVtZXNzYSDDqSBjb250cm9sYWRvIHBlbGEgcmVtZXNzYQpkZWYgb3BlcmFjYW8gPSBjdHguZ2V0QmVhbignc29jaW9QYXltZW50U2VydmljZScpLmlzU3RvcmVFbmFibGVkKCkKaWYgKG8uc3RhdHVzSW5mbyAhPSAnbWF0Y2hkYXk6ZXN0b3F1ZS1iYWl4YWRvJykgewogIG8uZW50cmllcy5lYWNoIHsgZSAtPgogICAgaWYgKG9wZXJhY2FvICYmIGUuY29uc2lnbm1lbnRFbnRyaWVzKSB7CiAgICAgIHJldHVybgogICAgfQogICAgZGVmIG5pdmVpcyA9IGZzLnNlYXJjaChuZXcgRmxleGlibGVTZWFyY2hRdWVyeSgKICAgICAgIlNFTEVDVCB7cGt9IEZST00ge1N0b2NrTGV2ZWx9IFdIRVJFIHtwcm9kdWN0Q29kZX09P2MiLCBbYzogZS5wcm9kdWN0LmNvZGVdKSkucmVzdWx0CiAgICBkZWYgbml2ZWwgPSBuaXZlaXMuZmluZCB7IGl0LndhcmVob3VzZT8uY29kZSA9PSAnd2FyZWhvdXNlX3MnIH0gPzogKG5pdmVpcyA/IG5pdmVpc1swXSA6IG51bGwpCiAgICBpZiAobml2ZWwpIHsKICAgICAgbml2ZWwuYXZhaWxhYmxlID0gTWF0aC5tYXgoMCwgbml2ZWwuYXZhaWxhYmxlIC0gKGUucXVhbnRpdHkgYXMgaW50KSkKICAgICAgbXMuc2F2ZShuaXZlbCkKICAgIH0KICB9CiAgby5zdGF0dXNJbmZvID0gJ21hdGNoZGF5OmVzdG9xdWUtYmFpeGFkbycKfQovLyBTw7NjaW8tdG9yY2Vkb3I6IHBlZGlkbyBjb20gcGxhbm8gcMO1ZSBvIGNsaWVudGUgbm8gZ3J1cG8gZG8gcGxhbm8gKGNhZGFzdHJvIGRlIHBsYW5vcyBkbyBCYWNrb2ZmaWNlKQp0cnkgewogIGN0eC5nZXRCZWFuKCdzb2Npb1BsYW5TZXJ2aWNlJykuYXBwbHlPcmRlcihvKQp9IGNhdGNoIChFeGNlcHRpb24gZXgpIHsKICBvcmcuc2xmNGouTG9nZ2VyRmFjdG9yeS5nZXRMb2dnZXIoJ3NvY2lvLXRvcmNlZG9yJykuZXJyb3IoJ0ZhbGhhIGFvIHRvcm5hciBvIGNsaWVudGUgc29jaW8gbm8gcGVkaWRvICcgKyBvLmNvZGUsIGV4KQp9Ci8vIEl0ZW0gMTQ6IHJlbWVzc2EgZW0gYWJlcnRvID0gcGVkaWRvICJFbSBwcm9jZXNzYW1lbnRvIiBhdMOpIG8gZW52aW8gb3UgYSByZXRpcmFkYQpkZWYgZmltID0gW0NvbnNpZ25tZW50U3RhdHVzLlNISVBQRUQsIENvbnNpZ25tZW50U3RhdHVzLlBJQ0tVUF9DT01QTEVURSwgQ29uc2lnbm1lbnRTdGF0dXMuQ0FOQ0VMTEVEXQppZiAob3BlcmFjYW8gJiYgby5jb25zaWdubWVudHM/LmFueSB7ICEoaXQuc3RhdHVzIGluIGZpbSkgfSkgewogIG8uc3RhdHVzID0gT3JkZXJTdGF0dXMuUkVBRFkKICBtcy5zYXZlKG8pCiAgcmV0dXJuICdXQUlUJwp9Cm8uc3RhdHVzID0gT3JkZXJTdGF0dXMuQ09NUExFVEVECm1zLnNhdmUobykKcmV0dXJuICdPSycKXV0+PC9zY3JpcHQ+CiAgICA8dHJhbnNpdGlvbiBuYW1lPSdPSycgdG89J3N1Y2Nlc3MnLz4KICAgIDx0cmFuc2l0aW9uIG5hbWU9J1dBSVQnIHRvPSd3YWl0Rm9yT3JkZXJBY3Rpb24nLz4KPC9zY3JpcHRBY3Rpb24+Cgo8IS0tIEl0ZW0gMTQ6IHRvZGFzIGFzIHJlbWVzc2FzIGVudmlhZGFzIG91IHJldGlyYWRhcyAodmVyaWZ5T3JkZXJDb21wbGV0aW9uIE9LKSAtLT4KPHNjcmlwdEFjdGlvbiBpZD0nZmluaXNoT3JkZXInPgogICAgPHNjcmlwdCB0eXBlPSdncm9vdnknPjwhW0NEQVRBWwppbXBvcnQgZGUuaHlicmlzLnBsYXRmb3JtLmNvcmUuUmVnaXN0cnkKaW1wb3J0IGRlLmh5YnJpcy5wbGF0Zm9ybS5jb3JlLmVudW1zLk9yZGVyU3RhdHVzCmRlZiBtcyA9IFJlZ2lzdHJ5LmFwcGxpY2F0aW9uQ29udGV4dC5nZXRCZWFuKCdtb2RlbFNlcnZpY2UnKQpkZWYgbyA9IHByb2Nlc3Mub3JkZXIKbXMucmVmcmVzaChvKQppZiAoby5zdGF0dXMgIT0gT3JkZXJTdGF0dXMuQ0FOQ0VMTEVEKSB7CiAgby5zdGF0dXMgPSBPcmRlclN0YXR1cy5DT01QTEVURUQKICBtcy5zYXZlKG8pCn0Kb3JnLnNsZjRqLkxvZ2dlckZhY3RvcnkuZ2V0TG9nZ2VyKCdmbGFtZW5nby1leHBlZGljYW8nKS5pbmZvKCdQZWRpZG8gJyArIG8uY29kZSArICc6IHJlbWVzc2FzIGZpbmFsaXphZGFzLCBwZWRpZG8gJyArIG8uc3RhdHVzKQpyZXR1cm4gJ09LJwpdXT48L3NjcmlwdD4KICAgIDx0cmFuc2l0aW9uIG5hbWU9J09LJyB0bz0nc3VjY2VzcycvPgo8L3NjcmlwdEFjdGlvbj4K'.decodeBase64(), 'UTF-8')

def out = []
def fs = flexibleSearchService
def SA = '<' + 'scriptAction'          // o filtro do HAC apaga a tag escrita inteira
def SA_FIM = '</' + 'scriptAction>'
def ativa = { fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {DynamicProcessDefinition} WHERE {code}='order-process' AND {active}=1")).result[0] }
def md5 = { String t -> java.security.MessageDigest.getInstance('MD5').digest(t.getBytes('UTF-8')).encodeHex().toString() }
def falha = { String m -> throw new IllegalStateException(m) }

def montar = { String c ->
	// 1. takePayment OK -> checkPayment
	def i = c.indexOf("<action id='takePayment'")
	def j = i < 0 ? -1 : c.indexOf("to='sourcePickup'", i)
	if (j < 0 || j > c.indexOf('</action>', i)) falha('takePayment -> sourcePickup não encontrado')
	c = c.substring(0, j) + "to='checkPayment'" + c.substring(j + "to='sourcePickup'".length())
	// 2. bloco sourcePickup (item 12) -> pagamento e remessas
	def a = c.indexOf('<!-- Item 12: remessa de retirada')
	def b = c.indexOf(SA + " id='sourcePickup'>")
	if (a < 0 || b < a) falha('bloco sourcePickup não encontrado')
	def e = c.indexOf(SA_FIM, b) + SA_FIM.length()
	c = c.substring(0, a) + PAGAMENTO.replaceAll(/\s+$/, '') + c.substring(e)
	// 3. completeOrder -> fechamento (completeOrder + finishOrder)
	def k = c.indexOf('    ' + SA + " id='completeOrder'>")
	if (k < 0) falha('completeOrder não encontrado')
	def l = c.indexOf(SA_FIM, k) + SA_FIM.length()
	c = c.substring(0, k) + FECHAMENTO.replaceAll(/\s+$/, '') + c.substring(l)
	// 4. verifyOrderCompletion OK -> finishOrder
	def v = c.indexOf("<action id='verifyOrderCompletion'")
	def ok = "<transition name='OK' to='postTaxes'/>"
	def w = v < 0 ? -1 : c.indexOf(ok, v)
	if (w < 0 || w > c.indexOf('</action>', v)) falha('verifyOrderCompletion -> postTaxes não encontrado')
	c = c.substring(0, w) + "<transition name='OK' to='finishOrder'/>" + c.substring(w + ok.length())
	return c
}

def conferir = { String novo ->
	def doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
			.parse(new ByteArrayInputStream(novo.getBytes('UTF-8')))
	def ids = [] as Set
	['action', 'scriptAction', 'wait', 'end'].each { t ->
		def l = doc.getElementsByTagName(t)
		for (x in 0..<l.length) { ids << l.item(x).getAttribute('id') }
	}
	def alvos = []
	['transition': 'to', 'wait': 'then', 'timeout': 'then', 'choice': 'then'].each { t, attr ->
		def l = doc.getElementsByTagName(t)
		for (x in 0..<l.length) { alvos << l.item(x).getAttribute(attr) }
	}
	def soltos = alvos.findAll { it && !(it in ids) }.unique()
	if (soltos) falha('transições para passos que não existem: ' + soltos)
	def gcl = new GroovyClassLoader(de.hybris.platform.core.Registry.class.classLoader)
	def nos = doc.getElementsByTagName('script')
	def compilados = []
	for (x in 0..<nos.length) {
		def n = nos.item(x)
		def id = n.parentNode.getAttribute('id')
		gcl.parseClass(n.textContent, id + '.groovy')
		compilados << id
	}
	out << 'XML ok, ' + ids.size() + ' passos, transições ok; scripts compilados: ' + compilados.join(', ')
	def takePay = (novo =~ /<action id='takePayment'[\s\S]*?<transition name='OK' to='([^']+)'/)[0][1]
	def verify = (novo =~ /<action id='verifyOrderCompletion'[\s\S]*?<transition name='OK' to='([^']+)'/)[0][1]
	out << 'takePayment OK -> ' + takePay + ' | verifyOrderCompletion OK -> ' + verify
}

if (ETAPA in ['conferir', 'aplicar']) {
	def p = ativa()
	out << "order-process ativo: v${p.version}, md5 ${md5(p.content)}"
	if (p.content.contains("id='checkPayment'")) {
		return (out + ['a versão ativa já tem o item 14: nada a fazer']).join('\n')
	}
	if (md5(p.content) != BASE_MD5) falha('a versão ativa não é a esperada (v6 do item 12, md5 ' + BASE_MD5 + '): pare e confira')
	def novo = montar(p.content)
	conferir(novo)
	out << 'versão nova: ' + novo.length() + ' caracteres (era ' + p.content.length() + ')'
	if (ETAPA == 'aplicar') {
		def bk = new File('/backup'); bk.mkdirs()
		def f = new File(bk, "order-process-v${p.version}-antes-operacao.xml")
		f.setText(p.content, 'UTF-8')
		def anterior = p.version
		p.content = novo
		modelService.save(p)
		def nova = ativa()
		out << "gravado: order-process v${nova.version} ativo (anterior v${anterior}, backup ${f.path}), checkPayment presente: " + nova.content.contains("id='checkPayment'")
	}
}

if (ETAPA == 'atendente') {
	def pos = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {PointOfService} WHERE {name}='warehouse_s'")).result[0]
	if (pos == null) falha('ponto de venda warehouse_s não encontrado')
	out << 'ponto de venda warehouse_s: depósitos ' + pos.warehouses*.code
	def g = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {StoreEmployeeGroup} WHERE {uid}='cdstoreemployees'")).result[0]
	if (g == null) {
		g = modelService.create(StoreEmployeeGroupModel)
		g.uid = 'cdstoreemployees'
	}
	g.name = 'Funcionários - Centro de distribuição (loja online)'
	g.store = pos
	modelService.save(g)
	def emp = fs.search(new FlexibleSearchQuery("SELECT {pk} FROM {Employee} WHERE {uid}='atendente.cd'")).result[0]
	if (emp == null) {
		emp = modelService.create(EmployeeModel)
		emp.uid = 'atendente.cd'
	}
	emp.name = 'Atendente Centro de distribuição'
	emp.loginDisabled = false
	emp.groups = [g, userService.getUserGroupForUID('warehouseagentgroup'), userService.getUserGroupForUID('warehouseagentrole')] as Set
	modelService.save(emp)
	out << 'atendente.cd nos grupos ' + emp.groups*.uid.sort() + ' (sem senha)'
}

if (ETAPA == 'traducoes') {
	def pt = new Locale('pt')
	['PAYMENT_NOT_CAPTURED': 'Aguardando pagamento', 'PAYMENT_CAPTURED': 'Pagamento confirmado', 'READY': 'Em processamento'].each { code, nome ->
		def ev = enumerationService.getEnumerationValue('OrderStatus', code)
		def antes = enumerationService.getEnumerationName(ev, pt)
		enumerationService.setEnumerationName(ev, nome, pt)
		out << "OrderStatus.${code}: pt '${antes}' -> '${nome}'"
	}
}

return out.join('\n')
