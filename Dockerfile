FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive \
    JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-25-jdk \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy WAR file
COPY agrishield/target/agrishield.war /app/agrishield.war

# Copy environment setup script
COPY docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["start"]
