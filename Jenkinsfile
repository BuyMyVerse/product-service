pipeline {
    agent {
        kubernetes {
            inheritFrom 'jenkins-agent'
            defaultContainer 'jnlp'
        }
    }

    environment {
        // ─── Remote Build VM ─────────────────────────────────────────────────
        REMOTE_HOST  = "3.226.177.66"
        REMOTE_USER  = "admin"
        SSH_CRED_ID  = "jenkins-agent-ssh-key"

        // ─── Nexus Registry ──────────────────────────────────────────────────
        NEXUS_HOST   = "dev-artifacthub.evaequitymtest.com"
        NEXUS_REPO   = "buymyverse-docker-dev"
        IMAGE_NAME   = "product-service"
        IMAGE_TAG    = "dev-${new Date().format('yyyy-MM-dd-HH-mm-ss')}"
        FULL_IMAGE   = "${NEXUS_HOST}/${NEXUS_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"

        // ─── Nexus Credentials (add in Jenkins → Credentials) ────────────────
        NEXUS_CRED_ID = "nexus-credentials"   // Jenkins credential ID
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {

        // ── Stage 1: Deployment Start Notification ────────────────────────────
        stage('Deployment Notification') {
            steps {
                echo "========================================================"
                echo "  🚀 DEPLOYMENT STARTED"
                echo "========================================================"
                echo "  Job        : ${JOB_NAME}"
                echo "  Build No   : #${BUILD_NUMBER}"
                echo "  Branch     : ${GIT_BRANCH}"
                echo "  Image Tag  : ${IMAGE_TAG}"
                echo "  Target VM  : ${REMOTE_USER}@${REMOTE_HOST}"
                echo "  Nexus Repo : ${NEXUS_HOST}/${NEXUS_REPO}"
                echo "  Started At : ${new Date()}"
                echo "========================================================"
            }
        }

        // ── Stage 2: Git Checkout ─────────────────────────────────────────────
        stage('Git Checkout') {
            steps {
                echo "📥 Checking out branch: ${GIT_BRANCH}"
                checkout scm
                echo "✅ Checked out commit: ${GIT_COMMIT}"
                echo "✅ Branch: ${GIT_BRANCH}"
            }
        }

        // ── Stage 3: Docker Build (on remote VM via SSH) ──────────────────────
        stage('Docker Build') {
            steps {
                echo "🐳 Building Docker image on remote VM: ${FULL_IMAGE}"
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

                            echo "=== Cleaning old workspace ==="
                            rm -rf /tmp/product-service-build
                            mkdir -p /tmp/product-service-build

                            echo "=== Cloning repo ==="
                            git clone --branch ${GIT_BRANCH} https://github.com/BuyMyVerse/product-service.git /tmp/product-service-build

                            echo "=== Building Docker image ==="
                            cd /tmp/product-service-build
                            docker build -t ${FULL_IMAGE} .

                            echo "✅ Docker build complete: ${FULL_IMAGE}"
                        '
                    """
                }
            }
        }

        // ── Stage 4: Docker Tag & Push to Nexus ──────────────────────────────
        stage('Docker Tag & Push to Nexus') {
            steps {
                echo "📤 Pushing image to Nexus: ${FULL_IMAGE}"
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: "${SSH_CRED_ID}",
                        keyFileVariable: 'SSH_KEY_FILE',
                        usernameVariable: 'SSH_USER'
                    ),
                    usernamePassword(
                        credentialsId: "${NEXUS_CRED_ID}",
                        usernameVariable: 'NEXUS_USER',
                        passwordVariable: 'NEXUS_PASS'
                    )
                ]) {
                    sh """
                        chmod 600 \$SSH_KEY_FILE

                        ssh -i \$SSH_KEY_FILE \\
                            -o StrictHostKeyChecking=no \\
                            -o BatchMode=yes \\
                            ${REMOTE_USER}@${REMOTE_HOST} '

                            echo "=== Logging in to Nexus ==="
                            echo "${NEXUS_PASS}" | docker login ${NEXUS_HOST} \\
                                --username ${NEXUS_USER} \\
                                --password-stdin

                            echo "=== Pushing image ==="
                            docker push ${FULL_IMAGE}

                            echo "=== Logging out ==="
                            docker logout ${NEXUS_HOST}

                            echo "✅ Pushed: ${FULL_IMAGE}"
                        '
                    """
                }
            }
        }

        // ── Stage 5: Verify Kubernetes Namespaces ─────────────────────────────
        stage('kubectl get ns') {
            steps {
                echo "☸️  Listing Kubernetes namespaces from agent pod..."
                sh """
                    kubectl get ns
                """
            }
        }
    }

    // ── Post: Cleanup & Final Status ──────────────────────────────────────────
    post {
        success {
            echo "========================================================"
            echo "  ✅ DEPLOYMENT SUCCESS"
            echo "  Image : ${FULL_IMAGE}"
            echo "  Build : #${BUILD_NUMBER}"
            echo "========================================================"
        }
        failure {
            echo "========================================================"
            echo "  ❌ DEPLOYMENT FAILED"
            echo "  Build : #${BUILD_NUMBER}"
            echo "  Check console output above for details."
            echo "========================================================"
        }
        always {
            echo "🧹 Cleaning workspace..."
            cleanWs()
        }
    }
}
