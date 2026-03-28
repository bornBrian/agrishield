FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY . .

RUN mvn -q -f agrishield/pom.xml clean package -DskipTests

FROM eclipse-temurin:25-jre

ENV TOMEE_VERSION=9.1.1
ENV CATALINA_HOME=/opt/tomee
ENV PATH="${CATALINA_HOME}/bin:${PATH}"

RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && mkdir -p /opt \
    && curl -fsSL "https://archive.apache.org/dist/tomee/tomee-${TOMEE_VERSION}/apache-tomee-${TOMEE_VERSION}-webprofile.tar.gz" -o /tmp/tomee.tar.gz \
    && tar -xzf /tmp/tomee.tar.gz -C /opt \
    && mv "/opt/apache-tomee-${TOMEE_VERSION}-webprofile" "${CATALINA_HOME}" \
    && rm -f /tmp/tomee.tar.gz \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/agrishield/target/agrishield.war ${CATALINA_HOME}/webapps/ROOT.war
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
