#!/bin/sh
# Container entrypoint for the coolstore monolith (WildFly on UBI8 OpenJDK 11)
#
# 1. Copies the read-only exploded deployment from /opt/app/ROOT.war into the
#    writable WildFly deployments directory (emptyDir volume under
#    readOnlyRootFilesystem on OpenShift).
# 2. Patches keycloak.json with KEYCLOAK_AUTH_SERVER_URL (externalized config;
#    the SPA's client-side OIDC adapter points at the Keycloak service).
# 3. Starts WildFly with the full platform config (JMS/Artemis included).
set -eu

JBOSS_HOME="${JBOSS_HOME:-/opt/jboss/wildfly}"
DEPLOY_DIR="${JBOSS_HOME}/standalone/deployments"
KC_URL="${KEYCLOAK_AUTH_SERVER_URL:-http://localhost:8081/}"

# Stage the exploded WAR into the writable deployments directory
rm -rf "${DEPLOY_DIR:?}"/*
cp -R /opt/app/ROOT.war "${DEPLOY_DIR}/ROOT.war"

# Externalize the Keycloak auth-server-url (was hardcoded to http://localhost:8081/)
KC_FILE="${DEPLOY_DIR}/ROOT.war/keycloak.json"
if [ -f "${KC_FILE}" ] && command -v sed >/dev/null 2>&1; then
    sed -i '/"auth-server-url"/c\    "auth-server-url": "'"${KC_URL}"'",' "${KC_FILE}"
fi

# Deploy marker for the exploded deployment
touch "${DEPLOY_DIR}/ROOT.war.dodeploy"

exec "${JBOSS_HOME}/bin/standalone.sh" -c standalone-full.xml -b 0.0.0.0 "$@"
