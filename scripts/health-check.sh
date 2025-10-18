#!/bin/bash

# Script de vérification de santé pour BantuOps Backend
# Usage: ./scripts/health-check.sh [host] [port]

set -euo pipefail

# Configuration par défaut
HOST=${1:-localhost}
PORT=${2:-8081}
TIMEOUT=${TIMEOUT:-10}
MAX_RETRIES=${MAX_RETRIES:-3}

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

# Fonction de vérification HTTP
check_http_endpoint() {
    local endpoint=$1
    local expected_status=${2:-200}
    local description=$3
    
    log "Vérification: $description"
    
    local response
    local http_code
    
    for ((i=1; i<=MAX_RETRIES; i++)); do
        if response=$(curl -s -w "%{http_code}" --connect-timeout "$TIMEOUT" "http://$HOST:$PORT$endpoint" 2>/dev/null); then
            http_code="${response: -3}"
            response_body="${response%???}"
            
            if [[ "$http_code" == "$expected_status" ]]; then
                success "$description - OK (HTTP $http_code)"
                return 0
            else
                warning "$description - HTTP $http_code (attendu: $expected_status)"
            fi
        else
            warning "$description - Tentative $i/$MAX_RETRIES échouée"
        fi
        
        if [[ $i -lt $MAX_RETRIES ]]; then
            sleep 2
        fi
    done
    
    error "$description - ÉCHEC après $MAX_RETRIES tentatives"
    return 1
}

# Fonction de vérification de la santé générale
check_health() {
    log "Vérification de la santé générale de l'application..."
    
    if check_http_endpoint "/actuator/health" 200 "Health Check"; then
        # Analyser la réponse JSON pour plus de détails
        local health_response
        health_response=$(curl -s --connect-timeout "$TIMEOUT" "http://$HOST:$PORT/actuator/health" 2>/dev/null || echo "{}")
        
        # Vérifier le statut global
        if echo "$health_response" | grep -q '"status":"UP"'; then
            success "Application en bonne santé"
        else
            error "Application en mauvaise santé"
            echo "$health_response" | jq '.' 2>/dev/null || echo "$health_response"
            return 1
        fi
        
        # Vérifier les composants individuels
        if echo "$health_response" | grep -q '"db":{"status":"UP"'; then
            success "Base de données - OK"
        else
            warning "Base de données - Problème détecté"
        fi
        
        if echo "$health_response" | grep -q '"redis":{"status":"UP"'; then
            success "Redis - OK"
        else
            warning "Redis - Problème détecté"
        fi
        
        return 0
    else
        return 1
    fi
}

# Fonction de vérification des métriques
check_metrics() {
    log "Vérification des métriques..."
    
    if check_http_endpoint "/actuator/metrics" 200 "Métriques Actuator"; then
        success "Métriques disponibles"
        return 0
    else
        return 1
    fi
}

# Fonction de vérification de l'info de l'application
check_info() {
    log "Vérification des informations de l'application..."
    
    if check_http_endpoint "/actuator/info" 200 "Informations de l'application"; then
        local info_response
        info_response=$(curl -s --connect-timeout "$TIMEOUT" "http://$HOST:$PORT/actuator/info" 2>/dev/null || echo "{}")
        
        # Extraire et afficher les informations importantes
        if command -v jq &> /dev/null; then
            echo "$info_response" | jq '.'
        else
            echo "$info_response"
        fi
        
        success "Informations de l'application récupérées"
        return 0
    else
        return 1
    fi
}

# Fonction de vérification de la connectivité réseau
check_network() {
    log "Vérification de la connectivité réseau..."
    
    # Test de ping
    if ping -c 1 -W "$TIMEOUT" "$HOST" &> /dev/null; then
        success "Connectivité réseau - OK"
    else
        error "Impossible de joindre l'hôte $HOST"
        return 1
    fi
    
    # Test de port
    if timeout "$TIMEOUT" bash -c "</dev/tcp/$HOST/$PORT"; then
        success "Port $PORT accessible"
    else
        error "Port $PORT inaccessible sur $HOST"
        return 1
    fi
    
    return 0
}

# Fonction de vérification des performances
check_performance() {
    log "Vérification des performances..."
    
    local start_time
    local end_time
    local response_time
    
    start_time=$(date +%s%N)
    
    if curl -s --connect-timeout "$TIMEOUT" "http://$HOST:$PORT/actuator/health" > /dev/null; then
        end_time=$(date +%s%N)
        response_time=$(( (end_time - start_time) / 1000000 ))
        
        if [[ $response_time -lt 1000 ]]; then
            success "Temps de réponse: ${response_time}ms - Excellent"
        elif [[ $response_time -lt 3000 ]]; then
            success "Temps de réponse: ${response_time}ms - Bon"
        elif [[ $response_time -lt 5000 ]]; then
            warning "Temps de réponse: ${response_time}ms - Acceptable"
        else
            error "Temps de réponse: ${response_time}ms - Lent"
            return 1
        fi
    else
        error "Impossible de mesurer le temps de réponse"
        return 1
    fi
    
    return 0
}

# Fonction de génération de rapport
generate_report() {
    local status=$1
    local timestamp=$(date +'%Y-%m-%d %H:%M:%S')
    
    cat << EOF

========================================
RAPPORT DE SANTÉ BANTUOPS BACKEND
========================================
Timestamp: $timestamp
Hôte: $HOST:$PORT
Statut global: $status
========================================

EOF
}

# Fonction principale
main() {
    log "Début de la vérification de santé BantuOps Backend"
    log "Cible: $HOST:$PORT"
    
    local overall_status="SUCCESS"
    local failed_checks=0
    
    # Vérifications individuelles
    if ! check_network; then
        overall_status="FAILURE"
        ((failed_checks++))
    fi
    
    if ! check_health; then
        overall_status="FAILURE"
        ((failed_checks++))
    fi
    
    if ! check_metrics; then
        overall_status="WARNING"
        ((failed_checks++))
    fi
    
    if ! check_info; then
        overall_status="WARNING"
        ((failed_checks++))
    fi
    
    if ! check_performance; then
        if [[ "$overall_status" != "FAILURE" ]]; then
            overall_status="WARNING"
        fi
        ((failed_checks++))
    fi
    
    # Génération du rapport final
    generate_report "$overall_status"
    
    if [[ "$overall_status" == "SUCCESS" ]]; then
        success "Toutes les vérifications sont passées avec succès"
        exit 0
    elif [[ "$overall_status" == "WARNING" ]]; then
        warning "$failed_checks vérification(s) ont échoué mais l'application fonctionne"
        exit 1
    else
        error "$failed_checks vérification(s) critiques ont échoué"
        exit 2
    fi
}

# Gestion des signaux
trap 'error "Vérification interrompue"; exit 130' INT TERM

# Exécution du script principal
main "$@"