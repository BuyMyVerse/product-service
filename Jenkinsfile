pipeline {

    // ── Use the existing pod template configured in Jenkins UI ───────────────
    // Manage Jenkins → Clouds → Kubernetes → Pod Templates → jenkins-agent
    agent {
        kubernetes {
            inheritFrom 'jenkins-agent'   // ← matches the Name field in your Pod template settings
            defaultContainer 'jenkins-agent'
        }
    }

    environment {
        REMOTE_HOST = "3.226.177.66"
        REMOTE_USER = "admin"
        SSH_CRED_ID = "jenkins-agent-ssh-key"   // Jenkins credential ID
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        timeout(time: 10, unit: 'MINUTES')
    }

    stages {

        // ── 1. Checkout ──────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                echo "📥 Checking out source inside EKS pod..."
                checkout scm
            }
        }

        // ── 2. SSH from pod into build VM & list home directory ───────────────
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
                            echo "======================================"
                            echo "  SSH Connection Successful!"
                            echo "  (from EKS Jenkins agent pod)"
                            echo "======================================"
                            echo "User     : \$(whoami)"
                            echo "Hostname : \$(hostname)"
                            echo "Home Dir : \$HOME"
                            echo ""
                            echo "Home Directory Contents:"
                            echo "--------------------------------------"
                            ls -lah \$HOME
                            echo "--------------------------------------"
                        '
                    """
                }
            }
        }
    }

    // ── Pod is automatically deleted by Jenkins after this block ─────────────
    post {
        success { echo "✅ Done. EKS pod deleted automatically." }
        failure  { echo "❌ Pipeline failed. EKS pod still cleaned up." }
        always   { cleanWs() }
    }
}
