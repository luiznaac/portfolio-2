#!/bin/sh
# Render the nginx site config from the template (so the ports are
# configurable at run time) and hand off to supervisord.
set -e

: "${API_PORT:=8080}"
: "${WEB_PORT:=8081}"
export API_PORT WEB_PORT

envsubst '${WEB_PORT} ${API_PORT}' \
  < /etc/nginx/templates/portfolio.conf.template \
  > /etc/nginx/conf.d/portfolio.conf

exec supervisord -n -c /etc/supervisor/supervisord.conf
