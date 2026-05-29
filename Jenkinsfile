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
        IMAGE_TAG     = "dev-${new Date().format('yyyy-MM-dd-HH-mm-ss')}"
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

        // ─────────────────────────────────────────────────────────────
        // Notification Function
        // ─────────────────────────────────────────────────────────────
        stage('Build Notification') {
            steps {
                script {

                    def sendNotification = { deployStatus ->

                        def commitMsg = sh(
                            script: "git log -1 --pretty=%B || echo 'No Commit Message'",
                            returnStdout: true
                        ).trim()

                        def committedBy = sh(
                            script: "git log -1 --pretty=%an || echo 'Unknown'",
                            returnStdout: true
                        ).trim()

                        def prUrl = env.CHANGE_URL ?: "N/A"

                        sh """
                        curl -s -X POST "${POWER_AUTOMATE_WEBHOOK}" \
                        -H "Content-Type: application/json" \
                        -d '{
                            "status": "${deployStatus}",
                            "job": "${JOB_NAME}",
                            "environment": "QA",
                            "branch": "${env.BRANCH_NAME}",
                            "committed_by": "${committedBy}",
                            "commit_message": "${commitMsg}",
                            "pr_url": "${prUrl}",
                            "image_tag": "${IMAGE_TAG}"
                        }'
                        """

                        echo "✅ Notification sent: ${deployStatus}"
                    }

                    env.SEND_NOTIFICATION = "true"

                    // Save method globally
                    binding.setVariable("sendNotification", sendNotification)

                    if (!env.CHANGE_ID) {
                        sendNotification("deployment_starting")
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────
        // Git Checkout
        // ─────────────────────────────────────────────────────────────
        stage('Git Checkout') {
            steps {
                script {
                    def targetBranch = env.CHANGE_ID ? env.CHANGE_BRANCH : env.BRANCH_NAME

                    echo "📥 Checking out branch: ${targetBranch}"

                    withCredentials([sshUserPrivateKey(
                        credentialsId: "${SSH_CRED_ID}",
                        keyFileVariable: 'SSH_KEY_FILE',
                        usernameVariable: 'SSH_USER'
                    )]) {

                        sh """
                            chmod 600 \$SSH_KEY_FILE

                            ssh -i \$SSH_KEY_FILE \
                            -o StrictHostKeyChecking=no \
                            ${REMOTE_USER}@${REMOTE_HOST} << 'ENDSSH'

                            cd ${PROJECT_PATH}

                            git fetch --all
                            git checkout ${targetBranch}
                            git pull origin ${targetBranch}

ENDSSH
                        """
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────
        // Docker Build
        // ─────────────────────────────────────────────────────────────
        stage('Docker Build') {
            steps {

                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {

                    sh """
                        chmod 600 \$SSH_KEY_FILE

                        ssh -i \$SSH_KEY_FILE \
                        -o StrictHostKeyChecking=no \
                        ${REMOTE_USER}@${REMOTE_HOST} << 'ENDSSH'

                        cd ${PROJECT_PATH}

                        docker build --no-cache -t ${FULL_IMAGE} .

ENDSSH
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────
        // Push Docker Image
        // ─────────────────────────────────────────────────────────────
        stage('Docker Push to Nexus') {
            when {
                not { changeRequest() }
            }

            steps {

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
                        ${REMOTE_USER}@${REMOTE_HOST} << ENDSSH

                        echo "\$NEXUS_PASS" | docker login ${NEXUS_HOST} \
                        --username "\$NEXUS_USER" --password-stdin

                        docker push ${FULL_IMAGE}

                        docker logout ${NEXUS_HOST}

ENDSSH
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────────────
        // Deploy to EKS
        // ─────────────────────────────────────────────────────────────
        stage('Deploy to EKS') {

            when {
                not { changeRequest() }
            }

            steps {

                withCredentials([sshUserPrivateKey(
                    credentialsId: "${SSH_CRED_ID}",
                    keyFileVariable: 'SSH_KEY_FILE',
                    usernameVariable: 'SSH_USER'
                )]) {

                    sh """
                        chmod 600 \$SSH_KEY_FILE

                        ssh -i \$SSH_KEY_FILE \
                        -o StrictHostKeyChecking=no \
                        ${REMOTE_USER}@${REMOTE_HOST} << 'ENDSSH'

                        kubectl set image deployment/${K8S_DEPLOYMENT} \
                        ${K8S_CONTAINER}=${FULL_IMAGE} \
                        -n ${K8S_NAMESPACE}

                        kubectl rollout status deployment/${K8S_DEPLOYMENT} \
                        -n ${K8S_NAMESPACE} --timeout=120s

ENDSSH
                    """
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Post Actions
    // ─────────────────────────────────────────────────────────────
    post {

        success {

            script {

                if (!env.CHANGE_ID) {
                    sendNotification("deployment_successful")
                }

                echo "✅ Deployment completed successfully"
            }
        }

        failure {

            script {

                if (!env.CHANGE_ID) {
                    sendNotification("deployment_failed")
                }

                echo "❌ Deployment failed"
            }
        }

        always {
            cleanWs()
        }
    }
}
