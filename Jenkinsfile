pipeline {
    agent {
        kubernetes {
            inheritFrom 'jenkins-agent'
            defaultContainer 'jnlp'
        }
    }

    parameters {
        choice(
            name: 'LABEL',
            choices: ['QA', 'Beta', 'Release'],
            description: 'Select build type for artifact publishing'
        )
        string(
            name: 'VERSION',
            defaultValue: '',
            description: 'Enter release version (Example: 1.0.0)'
        )
    }

    environment {
        REMOTE_HOST    = "3.226.177.66"
        REMOTE_USER    = "admin"
        SSH_CRED_ID    = "jenkins-agent-ssh-key"
        REMOTE_PATH    = "/home/admin/Jenkins-deployment/QA-Artifact-Push/product-service"
        GIT_BRANCH     = "Push-Artifact"
        NEXUS_BASE_URL = "https://dev-artifacthub.evaequitymtest.com/repository"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 15, unit: 'MINUTES')
    }

    stages {

        // ─── Validate Parameters ───────────────────────────────────────────
        stage('Validate Parameters') {
            steps {
                script {
                    if (!params.VERSION?.trim()) {
                        error("❌ VERSION parameter is required! Example: 1.0.0")
                    }

                    // Map LABEL → NEXUS_ENV + Maven version suffix
                    // QA      → buymyverse-maven-dev  + VERSION-SNAPSHOT
                    // Beta    → buymyverse-maven-dev  + VERSION-BETA
                    // Release → buymyverse-maven-prod + VERSION (plain semver)
                    switch (params.LABEL) {
                        case 'QA':
                            env.NEXUS_ENV     = 'dev'
                            env.NEXUS_REPO_ID = 'buymyverse-maven-dev'
                            env.MVN_VERSION   = "${params.VERSION}-SNAPSHOT"
                            break
                        case 'Beta':
                            env.NEXUS_ENV     = 'dev'
                            env.NEXUS_REPO_ID = 'buymyverse-maven-dev'
                            env.MVN_VERSION   = "${params.VERSION}-BETA"
                            break
                        case 'Release':
                            env.NEXUS_ENV     = 'prod'
                            env.NEXUS_REPO_ID = 'buymyverse-maven-prod'
                            env.MVN_VERSION   = "${params.VERSION}"
                            break
                        default:
                            error("❌ Unknown LABEL: ${params.LABEL}")
                    }

                    echo """
                    ╔══════════════════════════════════════════╗
                    ║         BUILD PARAMETERS RESOLVED        ║
                    ╠══════════════════════════════════════════╣
                    ║  LABEL      : ${params.LABEL}
                    ║  VERSION    : ${params.VERSION}
                    ║  MVN_VERSION: ${env.MVN_VERSION}
                    ║  NEXUS_ENV  : ${env.NEXUS_ENV}
                    ║  NEXUS_REPO : ${env.NEXUS_REPO_ID}
                    ╚══════════════════════════════════════════╝
                    """
                }
            }
        }

        stage('Checkout') {
            steps {
                echo "📥 Checking out source inside EKS pod..."
                checkout scm
            }
        }

        stage('SSH: Verify Connection') {
            steps {
                echo "🔐 Connecting from EKS pod → admin@${REMOTE_HOST}..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o BatchMode=yes \\
                            ${REMOTE_USER}@${REMOTE_HOST} '
                            echo "✅ SSH Connection Successful!"
                            ls -lah \$HOME
                        '
                    """
                }
            }
        }

        stage('CD: Navigate to Project Directory') {
            steps {
                echo "📂 Navigating to ${REMOTE_PATH}..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o BatchMode=yes \\
                            ${REMOTE_USER}@${REMOTE_HOST} '
                            cd ${REMOTE_PATH} || { echo "❌ Directory not found: ${REMOTE_PATH}"; exit 1; }
                            echo "✅ Current directory: \$(pwd)"
                            ls -lah
                        '
                    """
                }
            }
        }

        stage('Git: Checkout Branch') {
            steps {
                echo "🌿 Switching to branch ${GIT_BRANCH}..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o BatchMode=yes \\
                            ${REMOTE_USER}@${REMOTE_HOST} '
                            cd ${REMOTE_PATH} || { echo "❌ Directory not found: ${REMOTE_PATH}"; exit 1; }
                            git fetch origin
                            git checkout ${GIT_BRANCH} || git checkout -b ${GIT_BRANCH} origin/${GIT_BRANCH}
                            git pull origin ${GIT_BRANCH}
                            echo "✅ Branch : \$(git branch --show-current)"
                            echo "📝 Commit : \$(git log -1 --oneline)"
                        '
                    """
                }
            }
        }

        stage('Build: Maven Clean Install') {
            steps {
                echo "🔨 Running mvn clean install — version: ${env.MVN_VERSION}..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o BatchMode=yes \\
                            ${REMOTE_USER}@${REMOTE_HOST} '
                            cd ${REMOTE_PATH} || { echo "❌ Directory not found: ${REMOTE_PATH}"; exit 1; }
                            echo "🔨 Building version: ${env.MVN_VERSION}"
                            mvn versions:set -DnewVersion=${env.MVN_VERSION} -DgenerateBackupPoms=false
                            mvn clean install -DskipTests=false
                            echo "✅ Maven build completed!"
                        '
                    """
                }
            }
        }

        // ─── Deploy: pushes artifact to correct Nexus repo ────────────────
        stage('Deploy: Maven Clean Deploy → Nexus') {
            steps {
                echo "🚀 Deploying ${env.MVN_VERSION} → ${env.NEXUS_REPO_ID} (${env.NEXUS_ENV})..."
                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE
                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o BatchMode=yes \\
                            ${REMOTE_USER}@${REMOTE_HOST} '
                            cd ${REMOTE_PATH} || { echo "❌ Directory not found: ${REMOTE_PATH}"; exit 1; }

                            echo "🚀 Deploying to Nexus..."
                            echo "   Version  : ${env.MVN_VERSION}"
                            echo "   Repo     : ${env.NEXUS_REPO_ID}"
                            echo "   NEXUS_ENV: ${env.NEXUS_ENV}"

                            source .env

                            NEXUS_URL=${NEXUS_BASE_URL} \\
                            NEXUS_ENV=${env.NEXUS_ENV} \\
                            mvn clean deploy \\
                                -DNEXUS_URL=${NEXUS_BASE_URL} \\
                                -DNEXUS_ENV=${env.NEXUS_ENV} \\
                                -DskipTests=true

                            echo "✅ Deploy to ${env.NEXUS_REPO_ID} completed!"
                        '
                    """
                }
            }
        }
    }

    post {
        success {
            echo """
            ✅ Pipeline SUCCESS
            -------------------
            LABEL   : ${params.LABEL}
            VERSION : ${env.MVN_VERSION}
            REPO    : ${env.NEXUS_REPO_ID}
            """
        }
        failure  { echo "❌ Pipeline FAILED. Check logs above." }
        always   { cleanWs() }
    }
}
