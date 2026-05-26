pipeline {
    agent any

    environment {
        // ─── Docker / Registry ───────────────────────────────────────────────
        DOCKER_IMAGE      = "product-service"
        DOCKER_TAG        = "${BUILD_NUMBER}"
        DOCKER_REGISTRY   = "your-dockerhub-username"          // ← change this
        FULL_IMAGE        = "${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${DOCKER_TAG}"

        // ─── Remote Server ────────────────────────────────────────────────────
        REMOTE_HOST       = "3.226.177.66"        // ← change this
        REMOTE_USER       = "admin"                            // ← change this
        SSH_CRED_ID       = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIM1pJOF0xaB97eEgZkMxO0PagPTmEMqt9UZDhS+FM5M0 admin@ip-12-0-1-185"            // Jenkins credential ID (SSH Username with private key)

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

        // ── 2. Build JAR ───────────────────────────────────────────────────────
        stage('Build JAR') {
            steps {
                echo "🔨 Building Spring Boot JAR with Maven..."
                sh '''
                    mvn clean package -DskipTests \
                        --no-transfer-progress \
                        -f pom.xml
                '''
            }
            post {
                success { echo "✅ JAR build successful." }
                failure { error "❌ Maven build failed. Stopping pipeline." }
            }
        }

        // ── 3. Build Docker Image ──────────────────────────────────────────────
        stage('Build Docker Image') {
            steps {
                echo "🐳 Building Docker image: ${FULL_IMAGE}"
                sh "docker build -t ${FULL_IMAGE} ."
            }
        }

        // ── 4. Push to Registry ────────────────────────────────────────────────
        stage('Push Docker Image') {
            steps {
                echo "📤 Pushing Docker image to registry..."
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',   // ← Jenkins credential ID for Docker Hub
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

        // ── 5. Deploy on Remote Server via SSH ─────────────────────────────────
        stage('Deploy to Server') {
            steps {
                echo "🚀 Connecting to ${REMOTE_HOST} via SSH and deploying..."
                sshagent(credentials: ["${SSH_CRED_ID}"]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${REMOTE_USER}@${REMOTE_HOST} '

                            echo "=== Pulling latest image ==="
                            docker pull ${FULL_IMAGE}

                            echo "=== Stopping and removing old container (if any) ==="
                            docker stop ${CONTAINER_NAME} 2>/dev/null || true
                            docker rm   ${CONTAINER_NAME} 2>/dev/null || true

                            echo "=== Starting new container ==="
                            docker run -d \\
                                --name ${CONTAINER_NAME} \\
                                --restart unless-stopped \\
                                -p ${HOST_PORT}:${APP_PORT} \\
                                ${FULL_IMAGE}

                            echo "=== Container status ==="
                            docker ps --filter name=${CONTAINER_NAME}

                        '
                    """
                }
            }
            post {
                success { echo "✅ Deployment successful on ${REMOTE_HOST}." }
                failure { error "❌ Deployment failed on remote server." }
            }
        }

        // ── 6. Health Check ────────────────────────────────────────────────────
        stage('Health Check') {
            steps {
                echo "🩺 Waiting for application to be healthy..."
                sshagent(credentials: ["${SSH_CRED_ID}"]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${REMOTE_USER}@${REMOTE_HOST} '
                            for i in \$(seq 1 12); do
                                STATUS=\$(curl -s -o /dev/null -w "%{http_code}" http://localhost:${APP_PORT}/actuator/health 2>/dev/null || echo "000")
                                echo "Attempt \$i: HTTP \$STATUS"
                                if [ "\$STATUS" = "200" ]; then
                                    echo "✅ Application is healthy!"
                                    exit 0
                                fi
                                sleep 5
                            done
                            echo "❌ Health check timed out."
                            exit 1
                        '
                    """
                }
            }
        }

        // ── 7. Post-Deploy Cleanup on Server ───────────────────────────────────
        stage('Cleanup: Remove Old Images on Server') {
            steps {
                echo "🧹 Removing dangling and old images from remote server..."
                sshagent(credentials: ["${SSH_CRED_ID}"]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${REMOTE_USER}@${REMOTE_HOST} '

                            echo "=== Pruning unused Docker images ==="
                            docker image prune -f

                            echo "=== Removing previous image tags for this service ==="
                            docker images ${DOCKER_REGISTRY}/${DOCKER_IMAGE} \
                                --format "{{.Tag}} {{.ID}}" | \
                            awk -v keep="${DOCKER_TAG}" \'\$1 != keep {print \$2}\' | \
                            xargs -r docker rmi -f 2>/dev/null || true

                            echo "✅ Cleanup complete on server."
                        '
                    """
                }
            }
        }
    }

    // ── Post-Pipeline: Cleanup Jenkins Agent ──────────────────────────────────
    post {
        always {
            echo "🧹 Cleaning up Jenkins workspace and local Docker image..."
            sh '''
                docker rmi ${FULL_IMAGE} 2>/dev/null || true
                docker image prune -f   2>/dev/null || true
            '''
            cleanWs()
        }
        success {
            echo "🎉 Pipeline completed successfully! Image: ${FULL_IMAGE}"
        }
        failure {
            echo "🔥 Pipeline failed. Check logs above for details."
        }
    }
}
