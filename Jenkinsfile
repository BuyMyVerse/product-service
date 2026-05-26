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

        // ─────────────────────────────────────────────────────────────────
        // STAGE 0 — Validate & Resolve Parameters
        // ─────────────────────────────────────────────────────────────────
        stage('Validate Parameters') {
            steps {
                script {
                    // Guard: VERSION must not be blank
                    if (!params.VERSION?.trim()) {
                        error("❌ VERSION is required! Example: 1.0.0")
                    }

                    // Validate semver format  e.g. 1.0.0 or 1.2.3
                    if (!(params.VERSION ==~ /^\d+\.\d+\.\d+$/)) {
                        error("❌ VERSION must follow semver format: MAJOR.MINOR.PATCH (e.g. 1.0.0)")
                    }

                    // ── Map LABEL → Nexus repo + Maven version ──────────
                    switch (params.LABEL) {
                        case 'QA':
                            // SNAPSHOT policy repo — must end with -SNAPSHOT
                            env.NEXUS_ENV     = 'dev'
                            env.NEXUS_REPO_ID = 'buymyverse-maven-dev'
                            env.MVN_VERSION   = "${params.VERSION}-SNAPSHOT"
                            break

                        case 'Beta':
                            // Release policy repo — plain version with -BETA suffix
                            env.NEXUS_ENV     = 'beta'
                            env.NEXUS_REPO_ID = 'buymyverse-maven-beta'
                            env.MVN_VERSION   = "${params.VERSION}-BETA"
                            break

                        case 'Release':
                            // Release policy repo — plain semver, no suffix
                            env.NEXUS_ENV     = 'prod'
                            env.NEXUS_REPO_ID = 'buymyverse-maven-prod'
                            env.MVN_VERSION   = "${params.VERSION}"
                            break

                        default:
                            error("❌ Unknown LABEL: ${params.LABEL}")
                    }

                    echo """
                    ╔══════════════════════════════════════════╗
                    ║        BUILD PARAMETERS RESOLVED         ║
                    ╠══════════════════════════════════════════╣
                    ║  LABEL      : ${params.LABEL}
                    ║  VERSION    : ${params.VERSION}
                    ║  MVN_VERSION: ${env.MVN_VERSION}
                    ║  NEXUS_ENV  : ${env.NEXUS_ENV}
                    ║  NEXUS_REPO : ${env.NEXUS_REPO_ID}
                    ║  NEXUS_URL  : ${NEXUS_BASE_URL}
                    ╚══════════════════════════════════════════╝
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 1 — Checkout (EKS pod)
        // ─────────────────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                echo "📥 Checking out source inside EKS pod..."
                checkout scm
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 2 — SSH Verify Connection
        // ─────────────────────────────────────────────────────────────────
        stage('SSH: Verify Connection') {
            steps {
                echo "🔐 Connecting EKS pod → ${REMOTE_USER}@${REMOTE_HOST}..."
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
                                echo "🖥️  Host : \$(hostname)"
                                echo "📅 Date  : \$(date)"
                                ls -lah \$HOME
                            '
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 3 — Navigate to Project Directory
        // ─────────────────────────────────────────────────────────────────
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

        // ─────────────────────────────────────────────────────────────────
        // STAGE 4 — Git Checkout
        // ─────────────────────────────────────────────────────────────────
        stage('Git: Checkout Branch') {
            steps {
                echo "🌿 Switching to branch: ${GIT_BRANCH}..."
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
                                echo "🔄 Fetching latest from remote..."
                                git fetch origin
                                echo "🌿 Checking out: ${GIT_BRANCH}"
                                git checkout ${GIT_BRANCH} || git checkout -b ${GIT_BRANCH} origin/${GIT_BRANCH}
                                echo "⬇️  Pulling latest changes..."
                                git pull origin ${GIT_BRANCH}
                                echo "✅ Branch : \$(git branch --show-current)"
                                echo "📝 Commit : \$(git log -1 --oneline)"
                            '
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 5 — Maven Build
        // Sets version dynamically via versions:set before building
        // ─────────────────────────────────────────────────────────────────
        stage('Build: Maven Clean Install') {
            steps {
                echo "🔨 Building version: ${env.MVN_VERSION}..."
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

                                echo "🔖 Stamping version: ${env.MVN_VERSION}"
                                mvn versions:set \
                                    -DnewVersion=${env.MVN_VERSION} \
                                    -DgenerateBackupPoms=false \
                                    -DNEXUS_URL=${NEXUS_BASE_URL} \
                                    -DNEXUS_ENV=${env.NEXUS_ENV}

                                echo "🔨 Running mvn clean install..."
                                mvn clean install \
                                    -DskipTests=false \
                                    -DNEXUS_URL=${NEXUS_BASE_URL} \
                                    -DNEXUS_ENV=${env.NEXUS_ENV}

                                echo "✅ Build complete — version: ${env.MVN_VERSION}"
                            '
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // STAGE 6 — Maven Deploy → Nexus
        // Pushes artifact to the correct repo based on LABEL
        // ─────────────────────────────────────────────────────────────────
        stage('Deploy: Maven Clean Deploy → Nexus') {
            steps {
                echo "🚀 Deploying ${env.MVN_VERSION} → ${env.NEXUS_REPO_ID}..."
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
                                echo "   Label    : ${params.LABEL}"
                                echo "   Version  : ${env.MVN_VERSION}"
                                echo "   Repo     : ${env.NEXUS_REPO_ID}"
                                echo "   Nexus URL: ${NEXUS_BASE_URL}/buymyverse-maven-${env.NEXUS_ENV}/"

                                source .env

                                mvn deploy \
                                    -DskipTests=true \
                                    -DNEXUS_URL=${NEXUS_BASE_URL} \
                                    -DNEXUS_ENV=${env.NEXUS_ENV}

                                echo "✅ Successfully deployed to: ${env.NEXUS_REPO_ID}"
                                echo "📦 Artifact: com.buymyverse:product-service:${env.MVN_VERSION}"
                            '
                    """
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST
    // ─────────────────────────────────────────────────────────────────────
    post {
        success {
            echo """
            ╔══════════════════════════════════════════╗
            ║           ✅ PIPELINE SUCCESS             ║
            ╠══════════════════════════════════════════╣
            ║  LABEL   : ${params.LABEL}
            ║  VERSION : ${env.MVN_VERSION}
            ║  REPO    : ${env.NEXUS_REPO_ID}
            ║  URL     : ${NEXUS_BASE_URL}/buymyverse-maven-${env.NEXUS_ENV}/
            ╚══════════════════════════════════════════╝
            """
        }
        failure {
            echo """
            ╔══════════════════════════════════════════╗
            ║           ❌ PIPELINE FAILED              ║
            ╠══════════════════════════════════════════╣
            ║  LABEL   : ${params.LABEL}
            ║  VERSION : ${params.VERSION}
            ║  Check logs above for details.
            ╚══════════════════════════════════════════╝
            """
        }
        always {
            cleanWs()
        }
    }
}
