#!/bin/sh
# Aurora TV — голосовой мост для рутированной Алисы (Яндекс Станция).
#
# Демон читает поток событий из файла и отправляет их на встроенный HTTP-сервер
# Aurora TV (порт 8765). После разовой установки работает сам, без ПК.
#
# Использование:
#   aurora-voice.sh start                  — запустить в фоне
#   aurora-voice.sh stop                   — остановить
#   aurora-voice.sh status                 — состояние
#   aurora-voice.sh command "включи радио" — отправить команду вручную
#   aurora-voice.sh run                    — запустить в переднем плане (отладка)

set -u

BASE_DIR=/data/local/tmp/aurora_voice
EVENT_FILE="$BASE_DIR/events.tsv"
CONF_FILE=/data/local/etc/aurora-voice.conf
PID_FILE="$BASE_DIR/daemon.pid"
LOG_FILE=/data/local/tmp/aurora-voice.log

# Значения по умолчанию; переопределяются в $CONF_FILE.
TV_IP=192.168.0.3
TV_PORT=8765
POLL=1
AUTO_COMMAND=1
HEARTBEAT=60

[ -f "$CONF_FILE" ] && . "$CONF_FILE"

mkdir -p "$BASE_DIR" 2>/dev/null || true
[ -f "$EVENT_FILE" ] || : > "$EVENT_FILE"

BASE_URL="http://${TV_IP}:${TV_PORT}"

log() {
  msg="AuroraVoice: $*"
  if command -v logger >/dev/null 2>&1; then
    logger -t AuroraVoice -- "$*" 2>/dev/null || true
  fi
  echo "$(date '+%m-%d %H:%M:%S') $msg" >> "$LOG_FILE" 2>/dev/null || true
}

urlencode() {
  # Минимальная кодировка для JSON-строк: кавычки, слеши, переводы строк.
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\r//g'
}

post_json() {
  path="$1"
  payload="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -sS -m 5 -H 'Content-Type: application/json' -d "$payload" \
      "$BASE_URL$path" >/dev/null 2>&1 || log "curl failed: $path"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O /dev/null --header='Content-Type: application/json' \
      --post-data="$payload" "$BASE_URL$path" >/dev/null 2>&1 || log "wget failed: $path"
  else
    log "no HTTP client (curl/wget) available"
  fi
}

send_event() {
  phase="$1"
  text=$(urlencode "${2:-}")
  answer=$(urlencode "${3:-}")
  post_json /api/voice/event \
    "{\"phase\":\"$phase\",\"text\":\"$text\",\"answer\":\"$answer\"}"
}

send_command() {
  command_text=$(urlencode "$1")
  [ -z "$command_text" ] && return 0
  post_json /api/voice/command "{\"command\":\"$command_text\"}"
  log "command: $1"
}

connect() {
  post_json /api/voice/connect '{"connected":true}'
}

disconnect() {
  post_json /api/voice/connect '{"connected":false}' || true
}

handle_line() {
  kind="$1"
  shift
  text="$*"
  case "$kind" in
    LISTENING)
      send_event LISTENING "$text" ""
      ;;
    THINKING)
      send_event THINKING "$text" ""
      if [ "$AUTO_COMMAND" = "1" ] && [ -n "$text" ]; then
        case "$text" in
          *радио*|*канал*|*таймер*|*гид*) send_command "$text" ;;
          *включи*|*открой*|*поставь*|*выключи*) send_command "$text" ;;
        esac
      fi
      ;;
    ANSWERING)
      send_event ANSWERING "" "$text"
      ;;
    COMMAND)
      send_command "$text"
      ;;
    RADIO)
      send_command "включи радио"
      ;;
    TIMER)
      send_command "поставь таймер $text"
      ;;
    RHYTHM)
      send_command "ритм $text"
      ;;
    BEAT)
      intensity="${text:-1}"
      case "$intensity" in *[!0-9.]*) intensity=1 ;; esac
      post_json /api/voice/beat "{\"intensity\":$intensity}"
      ;;
    RESET)
      post_json /api/voice/reset '{}'
      ;;
    *)
      log "unknown event kind: $kind"
      ;;
  esac
}

read_new_events() {
  [ -f "$EVENT_FILE" ] || return 0
  lines=$(wc -l < "$EVENT_FILE" | tr -d ' ')
  [ -z "$lines" ] && return 0
  if [ "$lines" -gt "$last_line" ]; then
    start=$((last_line + 1))
    tail -n "+$start" "$EVENT_FILE" | while IFS='	' read -r kind text; do
      [ -z "$kind" ] && continue
      handle_line "$kind" "${text:-}"
    done
    last_line=$lines
  elif [ "$lines" -lt "$last_line" ]; then
    # Файл пересоздали — читаем с начала.
    last_line=0
  fi
}

run() {
  log "starting. TV=$BASE_URL eventFile=$EVENT_FILE"
  last_line=$(wc -l < "$EVENT_FILE" | tr -d ' ')
  [ -z "$last_line" ] && last_line=0
  trap 'disconnect; rm -f "$PID_FILE"; exit 0' INT TERM
  echo $$ > "$PID_FILE"
  connect
  last_beat=$(date +%s)
  while true; do
    read_new_events
    now=$(date +%s)
    elapsed=$((now - last_beat))
    if [ "$elapsed" -ge "$HEARTBEAT" ]; then
      connect
      last_beat=$now
    fi
    sleep "$POLL"
  done
}

start() {
  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "already running (pid $(cat "$PID_FILE"))"
    return 0
  fi
  nohup "$0" run >> "$LOG_FILE" 2>&1 &
  echo "started"
}

stop() {
  if [ -f "$PID_FILE" ]; then
    pid=$(cat "$PID_FILE")
    kill "$pid" 2>/dev/null || true
    rm -f "$PID_FILE"
    echo "stopped"
  else
    echo "not running"
  fi
}

case "${1:-run}" in
  start) start ;;
  stop) stop ;;
  status)
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      echo "running (pid $(cat "$PID_FILE")), TV=$BASE_URL"
    else
      echo "stopped"
    fi
    ;;
  command) shift; connect; send_command "$*"; sleep 1; disconnect ;;
  connect) connect ;;
  disconnect) disconnect ;;
  run) run ;;
  *) echo "usage: $0 {start|stop|status|command|connect|disconnect|run}" ;;
esac
