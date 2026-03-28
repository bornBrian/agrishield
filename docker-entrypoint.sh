#!/bin/sh
set -e

if [ -n "$PORT" ]; then
  sed -i "s/port=\"8080\"/port=\"$PORT\"/g" "$CATALINA_HOME/conf/server.xml"
fi

if [ -n "$JAVA_OPTS" ]; then
  export CATALINA_OPTS="$JAVA_OPTS"
fi

exec "$CATALINA_HOME/bin/catalina.sh" run
