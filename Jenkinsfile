pipeline {
    agent any

    environment {
        // ─── Docker / Registry ───────────────────────────────────────────────
        DOCKER_IMAGE      = "product-service"
        DOCKER_TAG        = "${BUILD_NUMBER}"
        DOCKER_REGISTRY   = "your-dockerhub-username"           // ← change to your Docker Hub username
        FULL_IMAGE        = "${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${DOCKER_TAG}"

        // ─── Remote Server (your actual server) ───────────────────────────────
        REMOTE_HOST       = "3.226.177.66"
        REMOTE_USER       = "admin"
        SSH_CRED_ID       = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIM1pJOF0xaB97eEgZkMxO0PagPTmEMqt9UZDhS+FM5M0 admin@ip-12-0-1-185"            // ← Jenkins credential ID (add private key here)

        // ─── Container / App ──────────────────────────────────────────────────
        CONTAINER_NAME    = "product-service-container"
        APP_PORT          = "8080"
        HOST_PORT         = "8080"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {

        // ── 1. Checkout ────────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                echo "📥 Checking out source from Jenkins branch..."
                checkout scm
            }
        }

        // ── 2. SSH: Verify Connection & List Home Directory ───────────────────
        // This proves SSH key auth works before any build starts
        stage('SSH: Verify Connection & List Home') {
            steps {
                echo "🔐 SSHing into admin@3.226.177.66 using jenkins-agent key..."
                sshagent(credentials: ["${SSH_CRED_ID}"]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${REMOTE_USER}@${REMOTE_HOST} '
                            echo "======================================"
                            echo "✅ SSH Connection Successful!"
                            echo "======================================"
                            echo "👤 User     : \$(whoami)"
                            echo "🖥️  Hostname : \$(hostname)"
                            echo "📁 Home Dir : \$HOME"
                            echo ""
                            echo "📂 Home directory contents:"
                            echo "--------------------------------------"
                            ls -lah \$HOME
                            echo "--------------------------------------"
                            echo ""
                            echo "🐳 Docker status:"
                            docker ps -a
                            echo ""
                            echo "💾 Disk usage:"
                            df -h /
                        '
                    """
                }
            }
        }

        // ── 3. Build JAR (Maven runs inside Docker — no mvn install needed) ───
        stage('Build JAR') {
            steps {
                echo "🔨 Building Spring Boot JAR using Maven Docker container..."
                sh '''
                    docker run --rm \
                        -v "$PWD":/app \
                        -v "$HOME/.m2":/root/.m2 \
                        -w /app \
                        maven:3.9.6-eclipse-temurin-17 \
                        mvn clean package -DskipTests --no-transfer-progress
                '''
            }
            post {
                success { echo "✅ JAR build successful." }
                failure { error "❌ Maven build failed. Stopping pipeline." }
            }
        }

        // ── 4. Build Docker Image ──────────────────────────────────────────────
        stage('Build Docker Image') {
            steps {
                echo "🐳 Building Docker image: ${FULL_IMAGE}"
                sh "docker build -t ${FULL_IMAGE} ."
            }
        }

        // ── 5. Push to Docker Hub ─────────────────────────────────────────────
        stage('Push Docker Image') {
            steps {
                echo "📤 Pushing Docker image to Docker Hub..."
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',    // ← Jenkins credential ID for Docker Hub
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push ${FULL_IMAGE}
                        docker logout
                    '''
                }
            }
        }

        // ── 6. Deploy Container on Remote Server ──────────────────────────────
        stage('Deploy to Server') {
            steps {
                echo "🚀 Deploying ${FULL_IMAGE} on 3.226.177.66..."
                sshagent(credentials: ["${SSH_CRED_ID}"]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${REMOTE_USER}@${REMOTE_HOST} '

                            echo "=== Pulling latest image ==="
                            docker pull ${FULL_IMAGE}

                            echo "=== Stopping old container (if running) ==="
                            docker stop ${CONTAINER_NAME} 2>/dev/null || true
                            docker rm   ${CONTAINER_NAME} 2>/dev/null || true

                            echo "=== Starting new container ==="
                            docker run -d \
                                --name ${CONTAINER_NAME} \
                                --restart unless-stopped \
                                -p ${HOST_PORT}:${APP_PORT} \
                                ${FULL_IMAGE}

                            echo "=== Running containers ==="
                            docker ps --filter name=${CONTAINER_NAME}
                        '
                    """
                }
            }
            post {
                success { echo "✅ Deployment successful on 3.226.177.66." }
                failure { error "❌ Deployment failed on remote server." }
            }
        }

        // ── 7. Health Check ───────────────────────────────────────────────────
        stage('Health Check') {
            steps {
                echo "🩺 Checking application health (12 attempts × 5s = 60s max)..."
                sshagent(credentials: ["${SSH_CRED_ID}"]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${REMOTE_USER}@${REMOTE_HOST} '
                            for i in \$(seq 1 12); do
                                STATUS=\$(curl -s -o /dev/null -w "%{http_code}" http://localhost:${APP_PORT}/actuator/health 2>/dev/null || echo "000")
                                echo "Attempt \$i/12 — HTTP \$STATUS"
                                if [ "\$STATUS" = "200" ]; then
                                    echo "✅ Application is healthy!"
                                    exit 0
                                fi
                                sleep 5
                            done
                            echo "❌ Health check timed out after 60s."
                            exit 1
                        '
                    """
                }
            }
        }

        // ── 8. Post-Deploy: Delete old images from server ─────────────────────
        stage('Cleanup: Delete Old Images on Server') {
            steps {
                echo "🧹 Removing old Docker images from 3.226.177.66..."
                sshagent(credentials: ["${SSH_CRED_ID}"]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${REMOTE_USER}@${REMOTE_HOST} '

                            echo "=== Pruning dangling/unused images ==="
                            docker image prune -f

                            echo "=== Removing old tags of product-service (keeping current: ${DOCKER_TAG}) ==="
                            docker images ${DOCKER_REGISTRY}/${DOCKER_IMAGE} \
                                --format "{{.Tag}} {{.ID}}" | \
                            awk -v keep="${DOCKER_TAG}" \'\$1 != keep {print \$2}\' | \
                            xargs -r docker rmi -f 2>/dev/null || true

                            echo "=== Images remaining on server ==="
                            docker images

                            echo "✅ Remote server cleanup complete."
                        '
                    """
                }
            }
        }
    }

    // ── Post-Pipeline: Clean Jenkins agent workspace & local image ────────────
    post {
        always {
            echo "🧹 Cleaning Jenkins agent — removing local image and workspace..."
            sh '''
                docker rmi ${FULL_IMAGE} 2>/dev/null || true
                docker image prune -f   2>/dev/null || true
            '''
            cleanWs()
        }
        success {
            echo "🎉 Pipeline SUCCESS! Live at http://3.226.177.66:8080"
        }
        failure {
            echo "🔥 Pipeline FAILED. Check console output above for details."
        }
    }
}
