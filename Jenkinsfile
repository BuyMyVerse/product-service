pipeline {

    // ── Run entire pipeline inside a temporary EKS pod ──────────────────────
    // Pod is created before the first stage and deleted automatically after post{}
    agent {
        kubernetes {
            label "product-service-agent-${BUILD_NUMBER}"
            defaultContainer 'jenkins-agent'
            yaml """
apiVersion: v1
kind: Pod
metadata:
  labels:
    app: jenkins-agent
    job: product-service
spec:
  # Pod will NOT restart after completion — it gets deleted by Jenkins
  restartPolicy: Never
  containers:
    - name: jenkins-agent
      image: jenkins/inbound-agent:latest
      tty: true
      resources:
        requests:
          cpu: "250m"
          memory: "256Mi"
        limits:
          cpu: "500m"
          memory: "512Mi"
      volumeMounts:
        - name: ssh-key-vol
          mountPath: /etc/ssh-key
          readOnly: true
  volumes:
    - name: ssh-key-vol
      secret:
        secretName: jenkins-agent-ssh-key   # ← Kubernetes secret (see setup below)
        defaultMode: 0600
"""
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
        success { echo "✅ Done. EKS pod will now be deleted automatically." }
        failure  { echo "❌ Pipeline failed. EKS pod will still be cleaned up." }
        always   { cleanWs() }
    }
}
