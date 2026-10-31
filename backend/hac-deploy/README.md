# Deploy da extensão `flamengomatchday` pelo HAC

Para atualizar o código da extensão no servidor **sem acesso ao host**: um script
Groovy, rodado no HAC (Console › Scripting), grava os arquivos novos dentro do
container. Depois o Emerson para o servidor, roda `ant all` e sobe.

## Gerar o script

```bash
# só conferir (não grava nada): o servidor está na versão BASE?
python3 backend/hac-deploy/gerar.py --base origin/flamengo --head minha-branch --rotulo pr52 --modo conferir --saida conferir.groovy

# aplicar
python3 backend/hac-deploy/gerar.py --base origin/flamengo --head minha-branch --rotulo pr52 --modo aplicar --saida aplicar.groovy

# auditoria: servidor == repositório? (todos os arquivos, não grava nada)
python3 backend/hac-deploy/gerar.py --auditar origin/flamengo --saida auditoria.groovy
```

`--base` é a versão que está **hoje** no servidor (normalmente a `flamengo`
antes do merge do PR); `--head` é a versão nova.

## O que o script faz (modo `aplicar`)

1. Confere o md5 de cada arquivo alterado no servidor contra a versão BASE
   (arquivo novo: confere que não existe). **Se algum divergir, para sem gravar nada.**
2. Backup da extensão inteira: `/backup/flamengomatchday-antes-<rótulo>-<data>/fontes.tgz`.
3. Grava os arquivos novos/alterados (`tar xzmf`, com data de agora para o
   `ant all` recompilar) e apaga os removidos.
4. Confere o md5 final contra a versão HEAD.

Saída esperada: `Conferência inicial: N/N ok` … `Conferência final: N/N ok` …
`OK. Falta: parar o servidor, ant all, subir.`

Para voltar atrás: `tar xzf /backup/.../fontes.tgz -C /opt/sap/hybris/bin/custom`
(pelo HAC, com `ProcessBuilder`) e `ant all` de novo.

## Depois do `ant all`

- Mudou `flamengomatchday-items.xml` ou ImpEx de `essentialdata`: HAC › Platform ›
  Update (Update running system + Localize types).
- Verificar na loja / Backoffice e rodar a auditoria (`--auditar`).

Testado em 31/10/2026: auditoria 59/59, recusa com md5 divergente e aplicação
idempotente com backup.
