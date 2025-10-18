#!/bin/bash

# Script de sauvegarde sécurisé pour BantuOps Backend
# Usage: ./scripts/backup.sh [type] [retention_days]

set -euo pipefail

# Configuration par défaut
BACKUP_TYPE=${1:-full}
RETENTION_DAYS=${2:-30}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="${PROJECT_DIR}/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

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
    log "Vérification des prérequis de sauvegarde..."
    
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
    
    # Créer le répertoire de sauvegarde
    mkdir -p "$BACKUP_DIR"
    
    # Vérifier les variables d'environnement
    if [[ -f "$PROJECT_DIR/.env" ]]; then
        source "$PROJECT_DIR/.env"
    else
        error "Fichier .env manquant"
        exit 1
    fi
    
    success "Prérequis validés"
}

# Fonction de sauvegarde de la base de données
backup_database() {
    log "Sauvegarde de la base de données PostgreSQL..."
    
    local db_backup_file="$BACKUP_DIR/db_backup_${TIMESTAMP}.sql"
    local db_backup_compressed="$BACKUP_DIR/db_backup_${TIMESTAMP}.sql.gz"
    
    # Vérifier que PostgreSQL est accessible
    if ! docker-compose exec -T postgres pg_isready -U "${DB_USERNAME}" -d "${DB_NAME}" &> /dev/null; then
        error "PostgreSQL n'est pas accessible"
        return 1
    fi
    
    # Effectuer la sauvegarde
    if docker-compose exec -T postgres pg_dump \
        -U "${DB_USERNAME}" \
        -d "${DB_NAME}" \
        --verbose \
        --no-password \
        --format=custom \
        --compress=9 \
        --no-privileges \
        --no-owner > "$db_backup_file"; then
        
        # Compression supplémentaire
        gzip "$db_backup_file"
        
        # Vérification de l'intégrité
        if [[ -f "$db_backup_compressed" ]] && [[ -s "$db_backup_compressed" ]]; then
            local backup_size=$(du -h "$db_backup_compressed" | cut -f1)
            success "Sauvegarde de la base de données créée: $(basename "$db_backup_compressed") ($backup_size)"
            
            # Chiffrement de la sauvegarde
            if [[ -n "${BACKUP_ENCRYPTION_KEY:-}" ]]; then
                encrypt_backup "$db_backup_compressed"
            fi
            
            return 0
        else
            error "Échec de la création de la sauvegarde de la base de données"
            return 1
        fi
    else
        error "Échec de la sauvegarde PostgreSQL"
        return 1
    fi
}

# Fonction de sauvegarde de Redis
backup_redis() {
    log "Sauvegarde de Redis..."
    
    local redis_backup_file="$BACKUP_DIR/redis_backup_${TIMESTAMP}.rdb"
    local redis_backup_compressed="$BACKUP_DIR/redis_backup_${TIMESTAMP}.rdb.gz"
    
    # Déclencher une sauvegarde Redis
    if docker-compose exec -T redis redis-cli BGSAVE; then
        # Attendre que la sauvegarde soit terminée
        while docker-compose exec -T redis redis-cli LASTSAVE | grep -q "$(docker-compose exec -T redis redis-cli LASTSAVE)"; do
            sleep 1
        done
        
        # Copier le fichier RDB
        if docker cp "$(docker-compose ps -q redis):/data/dump.rdb" "$redis_backup_file"; then
            # Compression
            gzip "$redis_backup_file"
            
            if [[ -f "$redis_backup_compressed" ]] && [[ -s "$redis_backup_compressed" ]]; then
                local backup_size=$(du -h "$redis_backup_compressed" | cut -f1)
                success "Sauvegarde Redis créée: $(basename "$redis_backup_compressed") ($backup_size)"
                
                # Chiffrement de la sauvegarde
                if [[ -n "${BACKUP_ENCRYPTION_KEY:-}" ]]; then
                    encrypt_backup "$redis_backup_compressed"
                fi
                
                return 0
            else
                error "Échec de la compression de la sauvegarde Redis"
                return 1
            fi
        else
            error "Échec de la copie du fichier RDB Redis"
            return 1
        fi
    else
        error "Échec du déclenchement de la sauvegarde Redis"
        return 1
    fi
}

# Fonction de sauvegarde des logs
backup_logs() {
    log "Sauvegarde des logs d'application..."
    
    local logs_backup_file="$BACKUP_DIR/logs_backup_${TIMESTAMP}.tar.gz"
    
    # Créer une archive des logs
    if docker-compose exec -T bantuops-backend tar -czf - /app/logs 2>/dev/null > "$logs_backup_file"; then
        if [[ -f "$logs_backup_file" ]] && [[ -s "$logs_backup_file" ]]; then
            local backup_size=$(du -h "$logs_backup_file" | cut -f1)
            success "Sauvegarde des logs créée: $(basename "$logs_backup_file") ($backup_size)"
            
            # Chiffrement de la sauvegarde
            if [[ -n "${BACKUP_ENCRYPTION_KEY:-}" ]]; then
                encrypt_backup "$logs_backup_file"
            fi
            
            return 0
        else
            error "Échec de la création de l'archive des logs"
            return 1
        fi
    else
        warning "Aucun log à sauvegarder ou erreur d'accès"
        return 0
    fi
}

# Fonction de sauvegarde de la configuration
backup_configuration() {
    log "Sauvegarde de la configuration..."
    
    local config_backup_file="$BACKUP_DIR/config_backup_${TIMESTAMP}.tar.gz"
    
    # Créer une archive de la configuration (sans les secrets)
    tar -czf "$config_backup_file" \
        -C "$PROJECT_DIR" \
        --exclude='.env' \
        --exclude='*.key' \
        --exclude='*.pem' \
        --exclude='*.p12' \
        docker-compose.yml \
        Dockerfile \
        src/main/resources/application*.properties \
        docker/ \
        scripts/ 2>/dev/null || true
    
    if [[ -f "$config_backup_file" ]] && [[ -s "$config_backup_file" ]]; then
        local backup_size=$(du -h "$config_backup_file" | cut -f1)
        success "Sauvegarde de la configuration créée: $(basename "$config_backup_file") ($backup_size)"
        return 0
    else
        error "Échec de la sauvegarde de la configuration"
        return 1
    fi
}

# Fonction de chiffrement des sauvegardes
encrypt_backup() {
    local backup_file=$1
    local encrypted_file="${backup_file}.enc"
    
    log "Chiffrement de la sauvegarde: $(basename "$backup_file")"
    
    if command -v openssl &> /dev/null; then
        if openssl enc -aes-256-cbc -salt -in "$backup_file" -out "$encrypted_file" -k "${BACKUP_ENCRYPTION_KEY}"; then
            # Supprimer le fichier non chiffré
            rm "$backup_file"
            success "Sauvegarde chiffrée: $(basename "$encrypted_file")"
        else
            error "Échec du chiffrement de la sauvegarde"
        fi
    else
        warning "OpenSSL non disponible, sauvegarde non chiffrée"
    fi
}

# Fonction de nettoyage des anciennes sauvegardes
cleanup_old_backups() {
    log "Nettoyage des sauvegardes anciennes (> $RETENTION_DAYS jours)..."
    
    local deleted_count=0
    
    # Supprimer les fichiers plus anciens que RETENTION_DAYS
    while IFS= read -r -d '' file; do
        rm "$file"
        ((deleted_count++))
        log "Supprimé: $(basename "$file")"
    done < <(find "$BACKUP_DIR" -name "*backup_*" -type f -mtime +$RETENTION_DAYS -print0 2>/dev/null)
    
    if [[ $deleted_count -gt 0 ]]; then
        success "$deleted_count ancienne(s) sauvegarde(s) supprimée(s)"
    else
        log "Aucune ancienne sauvegarde à supprimer"
    fi
}

# Fonction de vérification de l'intégrité
verify_backups() {
    log "Vérification de l'intégrité des sauvegardes..."
    
    local backup_count=0
    local verified_count=0
    
    for backup_file in "$BACKUP_DIR"/*backup_${TIMESTAMP}*; do
        if [[ -f "$backup_file" ]]; then
            ((backup_count++))
            
            # Vérifier que le fichier n'est pas vide
            if [[ -s "$backup_file" ]]; then
                ((verified_count++))
                success "Vérifiée: $(basename "$backup_file")"
            else
                error "Fichier vide: $(basename "$backup_file")"
            fi
        fi
    done
    
    if [[ $verified_count -eq $backup_count ]] && [[ $backup_count -gt 0 ]]; then
        success "Toutes les sauvegardes ($backup_count) sont intègres"
        return 0
    else
        error "$((backup_count - verified_count)) sauvegarde(s) corrompue(s) sur $backup_count"
        return 1
    fi
}

# Fonction de génération du rapport de sauvegarde
generate_backup_report() {
    local status=$1
    local report_file="$BACKUP_DIR/backup_report_${TIMESTAMP}.txt"
    
    cat > "$report_file" << EOF
========================================
RAPPORT DE SAUVEGARDE BANTUOPS BACKEND
========================================
Date: $(date +'%Y-%m-%d %H:%M:%S')
Type: $BACKUP_TYPE
Statut: $status
Rétention: $RETENTION_DAYS jours
========================================

FICHIERS DE SAUVEGARDE:
EOF
    
    # Lister les fichiers de sauvegarde créés
    for backup_file in "$BACKUP_DIR"/*backup_${TIMESTAMP}*; do
        if [[ -f "$backup_file" ]]; then
            local file_size=$(du -h "$backup_file" | cut -f1)
            echo "- $(basename "$backup_file") ($file_size)" >> "$report_file"
        fi
    done
    
    echo "" >> "$report_file"
    echo "Rapport généré: $(date +'%Y-%m-%d %H:%M:%S')" >> "$report_file"
    
    success "Rapport de sauvegarde généré: $(basename "$report_file")"
}

# Fonction principale
main() {
    log "Début de la sauvegarde BantuOps Backend"
    log "Type: $BACKUP_TYPE"
    log "Rétention: $RETENTION_DAYS jours"
    
    local overall_status="SUCCESS"
    local failed_operations=0
    
    # Vérifications préliminaires
    check_prerequisites
    
    # Effectuer les sauvegardes selon le type
    case "$BACKUP_TYPE" in
        "full")
            log "Sauvegarde complète en cours..."
            
            if ! backup_database; then
                overall_status="PARTIAL"
                ((failed_operations++))
            fi
            
            if ! backup_redis; then
                overall_status="PARTIAL"
                ((failed_operations++))
            fi
            
            if ! backup_logs; then
                overall_status="PARTIAL"
                ((failed_operations++))
            fi
            
            if ! backup_configuration; then
                overall_status="PARTIAL"
                ((failed_operations++))
            fi
            ;;
            
        "database")
            log "Sauvegarde de la base de données uniquement..."
            if ! backup_database; then
                overall_status="FAILURE"
                ((failed_operations++))
            fi
            ;;
            
        "redis")
            log "Sauvegarde de Redis uniquement..."
            if ! backup_redis; then
                overall_status="FAILURE"
                ((failed_operations++))
            fi
            ;;
            
        "logs")
            log "Sauvegarde des logs uniquement..."
            if ! backup_logs; then
                overall_status="FAILURE"
                ((failed_operations++))
            fi
            ;;
            
        *)
            error "Type de sauvegarde non reconnu: $BACKUP_TYPE"
            error "Types supportés: full, database, redis, logs"
            exit 1
            ;;
    esac
    
    # Vérification de l'intégrité
    if ! verify_backups; then
        overall_status="FAILURE"
        ((failed_operations++))
    fi
    
    # Nettoyage des anciennes sauvegardes
    cleanup_old_backups
    
    # Génération du rapport
    generate_backup_report "$overall_status"
    
    # Résultat final
    if [[ "$overall_status" == "SUCCESS" ]]; then
        success "Sauvegarde terminée avec succès!"
        exit 0
    elif [[ "$overall_status" == "PARTIAL" ]]; then
        warning "Sauvegarde terminée avec $failed_operations erreur(s)"
        exit 1
    else
        error "Sauvegarde échouée"
        exit 2
    fi
}

# Gestion des signaux
trap 'error "Sauvegarde interrompue"; exit 130' INT TERM

# Exécution du script principal
main "$@"