#!/bin/bash

# Script de déploiement sécurisé pour BantuOps Backend
# Usage: ./scripts/deploy.sh [environment] [version]

set -euo pipefail

# Configuration par défaut
ENVIRONMENT=${1:-production}
VERSION=${2:-latest}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Couleurs pour les logs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Fonction de logging
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Fonction de validation des prérequis
check_prerequisites() {
    log "Vérification des prérequis..."
    
    # Vérifier Docker
    if ! command -v docker &> /dev/null; then
        error "Docker n'est pas installé"
        exit 1
    fi
    
    # Vérifier Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        error "Docker Compose n'est pas installé"
        exit 1
    fi
    
    # Vérifier les fichiers de configuration
    if [[ ! -f "$PROJECT_DIR/.env" ]]; then
        error "Fichier .env manquant. Copiez .env.template vers .env et configurez les variables."
        exit 1
    fi
    
    # Vérifier les variables d'environnement critiques
    source "$PROJECT_DIR/.env"
    
    if [[ -z "${JWT_SECRET:-}" ]]; then
        error "JWT_SECRET n'est pas défini dans .env"
        exit 1
    fi
    
    if [[ -z "${ENCRYPTION_KEY:-}" ]]; then
        error "ENCRYPTION_KEY n'est pas défini dans .env"
        exit 1
    fi
    
    if [[ -z "${DB_PASSWORD:-}" ]] || [[ "${DB_PASSWORD}" == "CHANGEME_STRONG_PASSWORD" ]]; then
        error "DB_PASSWORD doit être configuré avec un mot de passe fort"
        exit 1
    fi
    
    success "Prérequis validés"
}

# Fonction de sauvegarde de la base de données
backup_database() {
    log "Création d'une sauvegarde de la base de données..."
    
    local backup_dir="$PROJECT_DIR/backups"
    local backup_file="backup_$(date +%Y%m%d_%H%M%S).sql"
    
    mkdir -p "$backup_dir"
    
    # Sauvegarde avec docker-compose
    docker-compose exec -T postgres pg_dump -U "${DB_USERNAME}" "${DB_NAME}" > "$backup_dir/$backup_file"
    
    if [[ $? -eq 0 ]]; then
        success "Sauvegarde créée: $backup_file"
        
        # Compression de la sauvegarde
        gzip "$backup_dir/$backup_file"
        success "Sauvegarde compressée: $backup_file.gz"
    else
        error "Échec de la sauvegarde de la base de données"
        exit 1
    fi
}

# Fonction de validation de la sécurité
validate_security() {
    log "Validation de la configuration de sécurité..."
    
    # Vérifier les permissions des fichiers sensibles
    if [[ -f "$PROJECT_DIR/.env" ]]; then
        local env_perms=$(stat -c "%a" "$PROJECT_DIR/.env")
        if [[ "$env_perms" != "600" ]]; then
            warning "Permissions du fichier .env: $env_perms (recommandé: 600)"
            chmod 600 "$PROJECT_DIR/.env"
            success "Permissions du fichier .env corrigées"
        fi
    fi
    
    # Vérifier la force des mots de passe
    source "$PROJECT_DIR/.env"
    
    if [[ ${#JWT_SECRET} -lt 32 ]]; then
        error "JWT_SECRET doit contenir au moins 32 caractères"
        exit 1
    fi
    
    if [[ ${#ENCRYPTION_KEY} -ne 32 ]]; then
        error "ENCRYPTION_KEY doit contenir exactement 32 caractères"
        exit 1
    fi
    
    success "Configuration de sécurité validée"
}

# Fonction de construction de l'image Docker
build_image() {
    log "Construction de l'image Docker..."
    
    cd "$PROJECT_DIR"
    
    # Construction avec cache et optimisations
    docker build \
        --build-arg VERSION="$VERSION" \
        --build-arg BUILD_DATE="$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
        --tag "bantuops-backend:$VERSION" \
        --tag "bantuops-backend:latest" \
        .
    
    if [[ $? -eq 0 ]]; then
        success "Image Docker construite avec succès"
    else
        error "Échec de la construction de l'image Docker"
        exit 1
    fi
}

# Fonction de test de l'image
test_image() {
    log "Test de l'image Docker..."
    
    # Test de démarrage rapide
    local container_id=$(docker run -d --rm \
        -e SPRING_PROFILES_ACTIVE=test \
        -e DB_HOST=localhost \
        -e REDIS_HOST=localhost \
        -e JWT_SECRET="test-secret-key-for-testing-purposes-only" \
        -e ENCRYPTION_KEY="test-encryption-key-32-chars-" \
        "bantuops-backend:$VERSION")
    
    # Attendre le démarrage
    sleep 10
    
    # Vérifier que le conteneur fonctionne
    if docker ps | grep -q "$container_id"; then
        success "Image Docker testée avec succès"
        docker stop "$container_id"
    else
        error "Échec du test de l'image Docker"
        docker logs "$container_id"
        exit 1
    fi
}

# Fonction de déploiement
deploy() {
    log "Déploiement de l'environnement $ENVIRONMENT..."
    
    cd "$PROJECT_DIR"
    
    # Arrêt des services existants
    log "Arrêt des services existants..."
    docker-compose down --remove-orphans
    
    # Nettoyage des images obsolètes
    log "Nettoyage des images obsolètes..."
    docker image prune -f
    
    # Démarrage des services
    log "Démarrage des services..."
    docker-compose up -d --build
    
    # Attendre que les services soient prêts
    log "Attente de la disponibilité des services..."
    local max_attempts=30
    local attempt=1
    
    while [[ $attempt -le $max_attempts ]]; do
        if curl -f -s "http://localhost:${MANAGEMENT_PORT:-8081}/actuator/health" > /dev/null; then
            success "Services démarrés avec succès"
            break
        fi
        
        log "Tentative $attempt/$max_attempts - En attente..."
        sleep 10
        ((attempt++))
    done
    
    if [[ $attempt -gt $max_attempts ]]; then
        error "Timeout: Les services ne sont pas disponibles"
        docker-compose logs
        exit 1
    fi
}

# Fonction de validation post-déploiement
validate_deployment() {
    log "Validation du déploiement..."
    
    # Test de santé de l'API
    local health_response=$(curl -s "http://localhost:${MANAGEMENT_PORT:-8081}/actuator/health")
    
    if echo "$health_response" | grep -q '"status":"UP"'; then
        success "API en bonne santé"
    else
        error "API en mauvaise santé: $health_response"
        exit 1
    fi
    
    # Test de connectivité à la base de données
    if echo "$health_response" | grep -q '"db":{"status":"UP"'; then
        success "Base de données connectée"
    else
        error "Problème de connexion à la base de données"
        exit 1
    fi
    
    # Test de connectivité à Redis
    if echo "$health_response" | grep -q '"redis":{"status":"UP"'; then
        success "Redis connecté"
    else
        error "Problème de connexion à Redis"
        exit 1
    fi
    
    success "Déploiement validé avec succès"
}

# Fonction de nettoyage en cas d'erreur
cleanup_on_error() {
    error "Erreur détectée, nettoyage en cours..."
    docker-compose down --remove-orphans
    exit 1
}

# Fonction principale
main() {
    log "Début du déploiement BantuOps Backend"
    log "Environnement: $ENVIRONMENT"
    log "Version: $VERSION"
    
    # Piège pour nettoyer en cas d'erreur
    trap cleanup_on_error ERR
    
    # Étapes de déploiement
    check_prerequisites
    validate_security
    
    # Sauvegarde uniquement en production
    if [[ "$ENVIRONMENT" == "production" ]]; then
        backup_database
    fi
    
    build_image
    test_image
    deploy
    validate_deployment
    
    success "Déploiement terminé avec succès!"
    log "L'application est disponible sur http://localhost:${APP_PORT:-8080}/api"
    log "Le monitoring est disponible sur http://localhost:${MANAGEMENT_PORT:-8081}/actuator"
}

# Exécution du script principal
main "$@"