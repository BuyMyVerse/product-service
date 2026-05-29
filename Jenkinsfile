def sendNotification(status) {

    def commitMsg = sh(
        script: "git log -1 --pretty=%B || echo 'No Commit Message'",
        returnStdout: true
    ).trim().replace('"', '\\"')

    def committedBy = sh(
        script: "git log -1 --pretty=%an || echo 'Unknown'",
        returnStdout: true
    ).trim()

    def prUrl = env.CHANGE_URL ?: "N/A"

    sh """
        curl -s -X POST "${env.POWER_AUTOMATE_WEBHOOK}" \
        -H "Content-Type: application/json" \
        -d '{
            "status": "${status}",
            "job": "${env.JOB_NAME}",
            "environment": "DEV",
            "branch": "${env.BRANCH_NAME}",
            "committed_by": "${committedBy}",
            "commit_message": "${commitMsg}",
            "pr_url": "${prUrl}",
            "image_tag": "${env.IMAGE_TAG}"
        }'
    """

    echo "✅ Notification sent: ${status}"
}

pipeline {

    agent {
        kubernetes {
            inheritFrom 'jenkins-agent'
            defaultContainer 'jnlp'
        }
    }

    environment {

        REMOTE_HOST   = "3.226.177.66"
        REMOTE_USER   = "admin"
        SSH_CRED_ID   = "jenkins-agent-ssh-key"
        PROJECT_PATH  = "/home/admin/Jenkins-deployment/product-service"

        NEXUS_HOST    = "dev-artifacthub.evaequitymtest.com"
        NEXUS_REPO    = "buymyverse-docker-dev"
        IMAGE_NAME    = "product-service"
        IMAGE_TAG     = "dev-${BUILD_NUMBER}"
        FULL_IMAGE    = "${NEXUS_HOST}/${NEXUS_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"
        NEXUS_CRED_ID = "nexus-credentials"

        K8S_NAMESPACE  = "buymyverse-dev"
        K8S_DEPLOYMENT = "product-service"
        K8S_CONTAINER  = "product-service"

        POWER_AUTOMATE_WEBHOOK = "https://defaulte3ce5830f7d140c0ab827ce4f99738.f0.environment.api.powerplatform.com:443/powerautomate/automations/direct/workflows/0417e5b8a7a747fd921d012dab200011/triggers/manual/paths/invoke?api-version=1&sp=%2Ftriggers%2Fmanual%2Frun&sv=1.0&sig=7UOHRrxDqoa8tcaV9n9-nQamG4hLXqSyJDzPx2zSbs8"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {

        // ─────────────────────────────────────────────
        // Build Information
        // ─────────────────────────────────────────────
        stage('Build Info') {

            steps {

                script {

                    echo "================================================="
                    echo "🚀 DEV Deployment Started"
                    echo "================================================="
                    echo "Job        : ${JOB_NAME}"
                    echo "Build No   : #${BUILD_NUMBER}"
                    echo "Branch     : ${env.BRANCH_NAME}"
                    echo "Image Tag  : ${IMAGE_TAG}"
                    echo "================================================="
                }
            }
        }

        // ─────────────────────────────────────────────
        // Git Checkout
        // ─────────────────────────────────────────────
        stage('Git Checkout') {

            steps {

                script {

                    def targetBranch = env.CHANGE_ID ? env.CHANGE_BRANCH : env.BRANCH_NAME

                    echo "📥 Checking out branch: ${targetBranch}"

                    withCredentials([
                        sshUserPrivateKey(
                            credentialsId: "${SSH_CRED_ID}",
                            keyFileVariable: 'SSH_KEY_FILE',
                            usernameVariable: 'SSH_USER'
                        )
                    ]) {

                        sh """
                            chmod 600 \$SSH_KEY_FILE

                            ssh -i \$SSH_KEY_FILE \
                            -o StrictHostKeyChecking=no \
                            ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'

                            cd ${PROJECT_PATH}

                            git fetch --all
                            git checkout ${targetBranch}
                            git pull origin ${targetBranch}

                            echo "Branch  : \$(git branch --show-current)"
                            echo "Commit  : \$(git rev-parse --short HEAD)"
                            echo "Message : \$(git log -1 --pretty=%B)"

ENDSSH
                        """
                    }
                }
            }
        }

        // ─────────────────────────────────────────────
        // Deployment Start Notification
        // ─────────────────────────────────────────────
        stage('Deployment Start Notification') {

            when {
                not { changeRequest() }
            }

            steps {

                script {

                    echo "🚀 Sending deployment start notification..."

                    sendNotification("started")
                }
            }
        }

        // ─────────────────────────────────────────────
        // Docker Build
        // ─────────────────────────────────────────────
        stage('Docker Build') {

            steps {

                echo "🐳 Building Docker image..."

                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: "${SSH_CRED_ID}",
                        keyFileVariable: 'SSH_KEY_FILE',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {

                    sh """
                        chmod 600 \$SSH_KEY_FILE

                        ssh -i \$SSH_KEY_FILE \
                        -o StrictHostKeyChecking=no \
                        ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'

                        cd ${PROJECT_PATH}

                        docker build --no-cache -t ${FULL_IMAGE} .

                        docker images | grep ${IMAGE_NAME}

                        echo "✅ Docker build completed"

ENDSSH
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        // Push Docker Image
        // ─────────────────────────────────────────────
        stage('Docker Push to Nexus') {

            when {
                not { changeRequest() }
            }

            steps {

                echo "📤 Pushing Docker image..."

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

                        ssh -i \$SSH_KEY_FILE \
                        -o StrictHostKeyChecking=no \
                        ${REMOTE_USER}@${REMOTE_HOST} bash -e << ENDSSH

                        echo "\$NEXUS_PASS" | docker login ${NEXUS_HOST} \
                        --username "\$NEXUS_USER" --password-stdin

                        docker push ${FULL_IMAGE}

                        docker logout ${NEXUS_HOST}

                        docker rmi ${FULL_IMAGE} || true

                        echo "✅ Docker push completed"

ENDSSH
                    """
                }
            }
        }

        // ─────────────────────────────────────────────
        // Deploy to EKS
        // ─────────────────────────────────────────────
        stage('Deploy to EKS') {

            when {
                not { changeRequest() }
            }

            steps {

                echo "☸️ Deploying to DEV EKS..."

                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: "${SSH_CRED_ID}",
                        keyFileVariable: 'SSH_KEY_FILE',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {

                    sh """
                        chmod 600 \$SSH_KEY_FILE

                        ssh -i \$SSH_KEY_FILE \
                        -o StrictHostKeyChecking=no \
                        ${REMOTE_USER}@${REMOTE_HOST} bash -e << 'ENDSSH'

                        echo "=== Updating Deployment ==="

                        kubectl set image deployment/${K8S_DEPLOYMENT} \
                        ${K8S_CONTAINER}=${FULL_IMAGE} \
                        -n ${K8S_NAMESPACE}

                        echo "=== Waiting for Rollout ==="

                        kubectl rollout status deployment/${K8S_DEPLOYMENT} \
                        -n ${K8S_NAMESPACE} --timeout=180s

                        echo "=== Current Running Image ==="

                        kubectl get deployment ${K8S_DEPLOYMENT} \
                        -n ${K8S_NAMESPACE} \
                        -o=jsonpath='{.spec.template.spec.containers[0].image}'

                        echo ""

                        echo "✅ Deployment completed successfully"

ENDSSH
                    """
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Post Actions
    // ─────────────────────────────────────────────
    post {

        success {

            script {

                if (!env.CHANGE_ID) {

                    echo "✅ Sending deployment completed notification..."

                    sendNotification("SUCCESS")
                }

                echo "✅ Build completed successfully"
            }
        }

        failure {

            script {

                if (!env.CHANGE_ID) {

                    echo "❌ Sending deployment failed notification..."

                    sendNotification("ended")
                }

                echo "❌ Build failed"
            }
        }

        always {

            echo "🧹 Cleaning workspace..."

            cleanWs()
        }
    }
}
