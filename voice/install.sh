#!/system/bin/sh
# Установка голосового моста Aurora TV на рутированную Алису.
# Запускать ОДИН РАЗ на самой станции (adb shell / Termux как root).
# Дальше демон стартует сам при включении — ПК не нужен.

set -u

SRC_DIR="${1:-$(pwd)}"
BIN_DIR=/data/local/bin
CONF_DIR=/data/local/etc
CONF_FILE="$CONF_DIR/aurora-voice.conf"
TV_IP="${TV_IP:-192.168.0.3}"
TV_PORT="${TV_PORT:-8765}"

if [ "$(id -u)" != "0" ]; then
  echo "Нужен root: su / adb root"
  exit 1
fi

for f in aurora-voice.sh emit.sh; do
  if [ ! -f "$SRC_DIR/$f" ]; then
    echo "Не найден $SRC_DIR/$f"
    exit 1
  fi
done

mkdir -p "$BIN_DIR" "$CONF_DIR" /data/local/tmp/aurora_voice

cp "$SRC_DIR/aurora-voice.sh" "$BIN_DIR/aurora-voice"
cp "$SRC_DIR/emit.sh" "$BIN_DIR/aurora-voice-emit"
chmod 755 "$BIN_DIR/aurora-voice" "$BIN_DIR/aurora-voice-emit"

if [ ! -f "$CONF_FILE" ]; then
  cat > "$CONF_FILE" <<EOF
# Aurora TV voice bridge — адрес телевизора
TV_IP=$TV_IP
TV_PORT=$TV_PORT
POLL=1
AUTO_COMMAND=1
HEARTBEAT=60
EOF
  chmod 644 "$CONF_FILE"
  echo "Создан конфиг: $CONF_FILE (TV_IP=$TV_IP)"
else
  echo "Конфиг уже есть: $CONF_FILE"
fi

# Magisk: сервис запускается после загрузки.
if [ -d /data/adb/service.d ]; then
  cat > /data/adb/service.d/aurora-voice.sh <<'EOF'
#!/system/bin/sh
# Aurora TV voice bridge autostart (Magisk service.d)
sleep 20
while [ ! -x /data/local/bin/aurora-voice ]; do sleep 10; done
/data/local/bin/aurora-voice start
EOF
  chmod 755 /data/adb/service.d/aurora-voice.sh
  echo "Автозапуск: Magisk service.d установлен"
elif [ -d /etc/init.d ]; then
  cat > /etc/init.d/aurora-voice <<'EOF'
#!/system/bin/sh
sleep 20
/data/local/bin/aurora-voice start
EOF
  chmod 755 /etc/init.d/aurora-voice
  echo "Автозапуск: /etc/init.d установлен"
else
  # Fallback для прошивок с поддержкой userinit.sh.
  touch /data/local/userinit.sh
  if ! grep -q 'aurora-voice' /data/local/userinit.sh; then
    echo '/data/local/bin/aurora-voice start' >> /data/local/userinit.sh
  fi
  chmod 755 /data/local/userinit.sh
  echo "Автозапуск: /data/local/userinit.sh (если поддерживается прошивкой)"
fi

"$BIN_DIR/aurora-voice" stop >/dev/null 2>&1 || true
"$BIN_DIR/aurora-voice" start
sleep 2
"$BIN_DIR/aurora-voice" status

echo ""
echo "Готово. Проверка с Алисы:"
echo "  /data/local/bin/aurora-voice command \"включи радио\""
echo "  /data/local/bin/aurora-voice-emit LISTENING \"включи НТВ плюс один\""
