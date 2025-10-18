# Multi-stage build pour optimiser la taille de l'image
FROM gradle:8.5-jdk21-alpine AS builder

# Définir le répertoire de travail
WORKDIR /app

# Copier les fichiers de configuration Gradle
COPY build.gradle settings.gradle ./
COPY gradle/ gradle/

# Télécharger les dépendances (mise en cache des couches Docker)
RUN gradle dependencies --no-daemon

# Copier le code source
COPY src/ src/

# Construire l'application
RUN gradle clean build -x test --no-daemon

# Image de production
FROM openjdk:21-jre-slim

# Créer un utilisateur non-root pour la sécurité
RUN groupadd -r bantuops && useradd -r -g bantuops bantuops

# Installer les outils nécessaires et nettoyer
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Définir le répertoire de travail
WORKDIR /app

# Copier l'artefact depuis l'étape de build
COPY --from=builder /app/build/libs/*.jar app.jar

# Créer les répertoires nécessaires
RUN mkdir -p /app/logs /app/config /app/data && \
    chown -R bantuops:bantuops /app

# Configurer les variables d'environnement par défaut
ENV JAVA_OPTS="-Xmx1024m -Xms512m -XX:+UseG1GC -XX:+UseContainerSupport" \
    SPRING_PROFILES_ACTIVE=production \
    SERVER_PORT=8080 \
    MANAGEMENT_PORT=8081

# Exposer les ports
EXPOSE 8080 8081

# Passer à l'utilisateur non-root
USER bantuops

# Healthcheck pour Docker
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Point d'entrée avec configuration sécurisée
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]