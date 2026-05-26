pipeline {
    agent {
        kubernetes {
            inheritFrom 'jenkins-agent'
            defaultContainer 'jnlp'
        }
    }

    environment {
        // ─── Remote Build VM ─────────────────────────────────────────────────
        REMOTE_HOST     = "3.226.177.66"
        REMOTE_USER     = "admin"
        SSH_CRED_ID     = "jenkins-agent-ssh-key"
        // Code already lives here on the VM — no git clone needed
        PROJECT_PATH    = "/home/admin/Jenkins-deployment/product-service"

        // ─── Nexus Registry ──────────────────────────────────────────────────
        NEXUS_HOST      = "dev-artifacthub.evaequitymtest.com"
        NEXUS_REPO      = "buymyverse-docker-dev"
        IMAGE_NAME      = "product-service"
        IMAGE_TAG       = "dev-${new Date().format('yyyy-MM-dd-HH-mm-ss')}"
        FULL_IMAGE      = "${NEXUS_HOST}/${NEXUS_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"
        NEXUS_CRED_ID   = "nexus-credentials"
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
                echo "  Source     : ${PROJECT_PATH}"
                echo "  Nexus Repo : ${NEXUS_HOST}/${NEXUS_REPO}"
                echo "  Started At : ${new Date()}"
                echo "========================================================"
            }
        }

        // ── Stage 2: Git Checkout (pull latest on remote VM) ──────────────────
        stage('Git Checkout') {
            steps {
                echo "📥 Pulling latest code on remote VM: branch ${GIT_BRANCH}"
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

                            echo "=== Pulling latest code ==="
                            cd ${PROJECT_PATH}
                            git fetch --all
                            git checkout ${GIT_BRANCH}
                            git pull origin ${GIT_BRANCH}

                            echo "✅ Branch  : \$(git branch --show-current)"
                            echo "✅ Commit  : \$(git rev-parse --short HEAD)"
                            echo "✅ Message : \$(git log -1 --pretty=%B)"
                        '
                    """
                }
            }
        }

        // ── Stage 3: Docker Build ─────────────────────────────────────────────
        stage('Docker Build') {
            steps {
                echo "🐳 Building Docker image: ${FULL_IMAGE}"
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

                            echo "=== Building Docker image ==="
                            cd ${PROJECT_PATH}
                            docker build -t ${FULL_IMAGE} .

                            echo "=== Image built ==="
                            docker images | grep product-service

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

                            echo "=== Pushing image to Nexus ==="
                            docker push ${FULL_IMAGE}

                            echo "=== Logging out ==="
                            docker logout ${NEXUS_HOST}

                            echo "=== Cleaning local image to free disk ==="
                            docker rmi ${FULL_IMAGE} 2>/dev/null || true

                            echo "✅ Pushed successfully: ${FULL_IMAGE}"
                        '
                    """
                }
            }
        }

        // ── Stage 5: kubectl get ns (run on VM via SSH) ───────────────────────
        // kubectl is on the VM, not in the jnlp pod — so SSH to run it
        stage('kubectl get ns') {
            steps {
                echo "☸️  Listing Kubernetes namespaces via VM..."
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
                            echo "=== Kubernetes Namespaces ==="
                            kubectl get ns
                        '
                    """
                }
            }
        }
    }

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
