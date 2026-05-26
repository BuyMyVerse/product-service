pipeline {

    agent {
        kubernetes {
            inheritFrom 'jenkins-agent'
            defaultContainer 'jnlp' 
        }
    }

    environment {
        REMOTE_HOST = "3.226.177.66"
        REMOTE_USER = "admin"
        SSH_CRED_ID = "jenkins-agent-ssh-key"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 10, unit: 'MINUTES')
    }

    stages {

        stage('Checkout') {
            steps {
                echo "📥 Checking out source inside EKS pod..."
                checkout scm
            }
        }

        stage('SSH: List Home Directory') {
            steps {
                echo "🔐 Connecting from EKS pod → admin@3.226.177.66..."
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
                            echo "  SSH Connection Successful!"
                            ls -lah \$HOME
                        '
                    """
                }
            }
        }
    }

    post {
        success { echo "✅ Done. EKS pod deleted automatically." }
        failure  { echo "❌ Pipeline failed. EKS pod still cleaned up." }
        always   { cleanWs() }
    }
}
