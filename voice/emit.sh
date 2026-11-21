#!/system/bin/sh
# Отправить событие голосового моста в демон (вызывать можно из хука/автоматизации).
#
# Примеры:
#   emit.sh LISTENING "включи НТВ плюс один"
#   emit.sh THINKING  "включи НТВ плюс один"
#   emit.sh ANSWERING "Включаю канал НТВ +1"
#   emit.sh COMMAND   "поставь таймер на 10 минут"
#   emit.sh RADIO
#   emit.sh RESET

set -u
BASE_DIR=/data/local/tmp/aurora_voice
EVENT_FILE="$BASE_DIR/events.tsv"
mkdir -p "$BASE_DIR" 2>/dev/null || true

kind="${1:-}"
[ -z "$kind" ] && { echo "usage: $0 {LISTENING|THINKING|ANSWERING|COMMAND|RADIO|TIMER|RESET} [text]"; exit 1; }
shift
printf '%s\t%s\n' "$kind" "$*" >> "$EVENT_FILE"
