#!/usr/bin/env bash
# =====================================================================
#  Instala (ou atualiza) a extensão flamengomatchday no SAP Commerce
#  que roda no container Docker.
#
#  Rodar no servidor (host do Docker), de dentro da pasta do repositório:
#     ./backend/deploy-flamengomatchday.sh            # container "flamengo"
#     ./backend/deploy-flamengomatchday.sh outro-nome  # outro container
#
#  O que faz:
#   1. copia backend/flamengomatchday para bin/custom dentro do container
#   2. registra a extensão no config/localextensions.xml (só na 1a vez)
#   3. para o servidor
#   4. build (ant all): gera o tipo SeatHold e compila o código
#   5. sobe o servidor
#  Depois disso falta o "Update" no HAC (ver docs/flamengo-matchday/README.md).
# =====================================================================
set -euo pipefail

CONTAINER="${1:-flamengo}"
HYBRIS=/opt/sap/hybris
EXT=flamengomatchday
SRC="$(cd "$(dirname "$0")" && pwd)/$EXT"
RUN="docker exec -u hybris $CONTAINER bash -lc"

[ -f "$SRC/extensioninfo.xml" ] || { echo "Não achei $SRC"; exit 1; }
docker inspect "$CONTAINER" >/dev/null || { echo "Container $CONTAINER não existe"; exit 1; }

echo "== 1/5 Copiando a extensão para $CONTAINER:$HYBRIS/bin/custom/$EXT"
$RUN "rm -rf $HYBRIS/bin/custom/$EXT"
docker cp "$SRC" "$CONTAINER:$HYBRIS/bin/custom/$EXT"
docker exec -u root "$CONTAINER" chown -R hybris:hybris "$HYBRIS/bin/custom/$EXT"

echo "== 2/5 Registrando no localextensions.xml"
$RUN "cd $HYBRIS/config && if grep -q \"name='$EXT'\" localextensions.xml; then echo 'já registrada'; else cp localextensions.xml localextensions.xml.bak-antes-$EXT && sed -i \"s#<extension name='spartacussampledata' />#<extension name='spartacussampledata' />\n    <extension name='$EXT' />#\" localextensions.xml && grep -n \"$EXT\" localextensions.xml; fi"

echo "== 3/5 Parando o servidor"
$RUN "cd $HYBRIS/bin/platform && ./hybrisserver.sh stop" || true

echo "== 4/5 Build (ant all) — leva alguns minutos"
$RUN "cd $HYBRIS/bin/platform && . ./setantenv.sh && ant all"

echo "== 5/5 Subindo o servidor"
$RUN "cd $HYBRIS/bin/platform && ./hybrisserver.sh start"

echo
echo "Pronto. Quando o HAC voltar (alguns minutos), faça o Update:"
echo "  HAC > Platform > Update > marque 'Update running system' e 'Localize types' > Update"
