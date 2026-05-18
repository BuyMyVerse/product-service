pipeline {
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        ECR_REPO_NAME  = 'product-service'
        IMAGE_TAG      = "${env.BRANCH_NAME}-${env.BUILD_NUMBER}"
    }

    stages {

        // ─────────────────────────────────────────
        // STAGE 1: Checkout
        // ─────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                echo "Branch  : ${env.BRANCH_NAME}"
                echo "Build No: ${env.BUILD_NUMBER}"
            }
        }

        // ─────────────────────────────────────────
        // STAGE 2: Docker Image Build
        // ─────────────────────────────────────────
        stage('Docker Image Build') {
            steps {
                script {
                    echo "Building Docker image: ${ECR_REPO_NAME}:${IMAGE_TAG}"
                    sh """
                        docker build \
                            --tag ${ECR_REPO_NAME}:${IMAGE_TAG} \
                            --no-cache \
                            .
                    """
                    echo "Docker image built successfully: ${ECR_REPO_NAME}:${IMAGE_TAG}"
                }
            }
        }
    }

    post {
        success { echo "✅ Image built: ${ECR_REPO_NAME}:${IMAGE_TAG}" }
        failure { echo "❌ Build failed on branch: ${env.BRANCH_NAME}" }
        always  { cleanWs() }
    }
}
