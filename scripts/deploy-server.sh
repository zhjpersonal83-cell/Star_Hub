#!/usr/bin/env bash
# Run as root on the target server after uploading the WAR and deploy/ to a staging directory.
set -Eeuo pipefail
stage=${1:?Usage: deploy-server.sh STAGING_DIRECTORY}
stage=$(realpath "$stage")
[[ $EUID -eq 0 ]] || { echo 'Run as root.' >&2; exit 1; }
test -s "$stage/starhub.war"
test -s "$stage/nginx.conf"
test -s "$stage/starhub-proxy.conf"
test -s /etc/letsencrypt/live/starhub.zhongjiehuang.com/fullchain.pem
test -d /var/lib/tomcat9/webapps
unzip -t "$stage/starhub.war" >/dev/null
backup="/var/backups/starhub/$(date -u +%Y%m%dT%H%M%SZ)"
install -d -m 700 "$backup"
[[ ! -f /var/lib/tomcat9/webapps/starhub.war ]] || cp -p /var/lib/tomcat9/webapps/starhub.war "$backup/starhub.war"
[[ ! -f /etc/nginx/sites-available/starhub ]] || cp -p /etc/nginx/sites-available/starhub "$backup/nginx.conf"
[[ ! -f /etc/nginx/snippets/starhub-proxy.conf ]] || cp -p /etc/nginx/snippets/starhub-proxy.conf "$backup/starhub-proxy.conf"
stopped=0
rollback() {
  status=$?
  trap - ERR
  echo "Deployment failed; restoring application and starting Tomcat9. Backup: $backup" >&2
  if [[ $stopped -eq 1 ]]; then
    systemctl stop tomcat9 || true
    rm -rf /var/lib/tomcat9/webapps/starhub /var/lib/tomcat9/webapps/starhub.war
    [[ ! -f "$backup/starhub.war" ]] || cp -p "$backup/starhub.war" /var/lib/tomcat9/webapps/starhub.war
    [[ ! -f "$backup/nginx.conf" ]] || cp -p "$backup/nginx.conf" /etc/nginx/sites-available/starhub
    [[ ! -f "$backup/starhub-proxy.conf" ]] || cp -p "$backup/starhub-proxy.conf" /etc/nginx/snippets/starhub-proxy.conf
    systemctl start tomcat9 || true
    if nginx -t; then systemctl reload nginx || true; fi
  fi
  exit "$status"
}
trap rollback ERR
echo "$(date -u +%FT%TZ) STOP_TOMCAT9"
systemctl stop tomcat9
stopped=1
if systemctl is-active --quiet tomcat9; then echo 'Tomcat9 is still active' >&2; false; fi
echo "$(date -u +%FT%TZ) CLEAR_TOMCAT_FILE_LOGS"
# Keep active files/inodes for rsyslog; remove rotated archives. No unrelated service logs are touched.
find /var/log/tomcat9 -type f -exec truncate -s 0 -- {} +
find /var/log/tomcat9 -type f \( -name '*.gz' -o -name '*.zip' -o -name '*.out.[0-9]*' \) -delete
echo "$(date -u +%FT%TZ) INSTALL_STARHUB"
rm -rf /var/lib/tomcat9/webapps/starhub /var/cache/tomcat9/Catalina/localhost/starhub
install -o tomcat -g tomcat -m 640 "$stage/starhub.war" /var/lib/tomcat9/webapps/starhub.war
install -m 644 "$stage/starhub-proxy.conf" /etc/nginx/snippets/starhub-proxy.conf
install -m 644 "$stage/nginx.conf" /etc/nginx/sites-available/starhub
ln -sfn /etc/nginx/sites-available/starhub /etc/nginx/sites-enabled/starhub
nginx -t
installed_at=$(date +%s)
echo "$(date -u +%FT%TZ) DEPLOYED_WAIT_30_SECONDS"
sleep 30
elapsed=$(( $(date +%s) - installed_at ))
echo "$(date -u +%FT%TZ) START_TOMCAT9 waited=${elapsed}s"
systemctl start tomcat9
healthy=0
for attempt in {1..45}; do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/starhub/api/health >/dev/null 2>&1; then healthy=1; break; fi
  sleep 2
done
[[ "$healthy" == 1 ]]
systemctl reload nginx
https_ready=0
# Nginx reload sends a signal; old workers may still serve requests for a moment.
for attempt in {1..15}; do
  if curl --fail --silent --show-error --max-time 5 --resolve starhub.zhongjiehuang.com:443:127.0.0.1 https://starhub.zhongjiehuang.com/api/health 2>/dev/null; then https_ready=1; break; fi
  sleep 1
done
[[ "$https_ready" == 1 ]]
echo
echo "$(date -u +%FT%TZ) DEPLOYMENT_OK backup=$backup"
trap - ERR
