pipeline {
    agent any

    environment {
        AWS_REGION      = 'us-east-1'
        ECR_REGISTRY    = '909783398453.dkr.ecr.us-east-1.amazonaws.com'
        ECR_REPO        = 'buymyverse/product-service'
        
        AWS_ACCESS_KEY  = credentials('aws-access-key-id')
        AWS_SECRET_KEY  = credentials('aws-secret-access-key')
        TEAMS_URL       = credentials('jenkins-cicd-webhook-url')

        REPO_URL        = 'https://github.com/BuyMyVerse/product-service'
        BUILDER_HOST    = '3.226.177.66'
        BUILDER_USER    = 'admin'
        PROJECT_DIR     = '/home/admin/Jenkins-deployment/product-service'
        K8S_NAMESPACE   = 'buymyverse-dev'
        K8S_DEPLOYMENT  = 'product-service'
        K8S_CONTAINER   = 'product-service'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
        timestamps()
    }

    stages {

        stage('Prepare Metadata') {
            steps {
                script {
                    env.COMMITTED_BY = sh(
                        script: 'git log -1 --pretty=format:"%an"',
                        returnStdout: true
                    ).trim()

                    env.COMMIT_MSG = sh(
                        script: 'git log -1 --pretty=format:"%s"',
                        returnStdout: true
                    ).trim()

                    env.SOURCE_BRANCH = sh(
                        script: 'git log -1 --merges --pretty=format:"%s" | grep -oP "Merge pull request #\\d+ from \\K\\S+" || echo "${BRANCH_NAME}"',
                        returnStdout: true
                    ).trim()

                    env.JOB_SHORT = env.JOB_NAME.tokenize('/').size() > 1 ?
                        env.JOB_NAME.tokenize('/')[1] :
                        env.JOB_NAME

                    env.PR_NUMBER = sh(
                        script: '''
                            git log -1 --pretty=format:"%s" | grep -oP "(?:Merge pull request #|\\(#)\\K\\d+" | head -1 || \
                            git log --merges --pretty=format:"%s" -10 | grep -oP "Merge pull request #\\K\\d+" | head -1 || \
                            echo ""
                        ''',
                        returnStdout: true
                    ).trim()

                    env.COMMIT_HASH = sh(
                        script: 'git log -1 --pretty=format:"%H"',
                        returnStdout: true
                    ).trim()

                    def prNum = env.PR_NUMBER?.trim()
                    if (env.CHANGE_URL) {
                        env.PR_URL = env.CHANGE_URL
                    } else if (prNum && prNum != '' && prNum != 'null') {
                        env.PR_URL = "${env.REPO_URL}/pull/${prNum}"
                    } else {
                        env.PR_URL = "${env.REPO_URL}/tree/${env.BRANCH_NAME}"
                    }

                    env.ACTUAL_BRANCH = env.CHANGE_BRANCH ?: env.BRANCH_NAME

                    env.IMAGE_TAG = "qa-" + new Date().format("yyyy-MM-dd-HH-mm-ss")

                    echo "============================================="
                    echo "COMMITTED_BY  : ${env.COMMITTED_BY}"
                    echo "SOURCE_BRANCH : ${env.SOURCE_BRANCH}"
                    echo "ACTUAL_BRANCH : ${env.ACTUAL_BRANCH}"
                    echo "COMMIT_MSG    : ${env.COMMIT_MSG}"
                    echo "PR_NUMBER     : ${env.PR_NUMBER}"
                    echo "COMMIT_HASH   : ${env.COMMIT_HASH}"
                    echo "PR_URL        : ${env.PR_URL}"
                    echo "IMAGE_TAG     : ${env.IMAGE_TAG}"
                    echo "=============================================="
                }
            }
        }

        stage('Deployment Notification') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'qa'
                }
            }
            steps {
                echo 'Sending Deployment Started Notification...'
                sh """
                    curl -s -X POST "${TEAMS_URL}" \\
                    -H "Content-Type: application/json" \\
                    -d '{
                        "status": "started",
                        "job": "${env.JOB_SHORT}",
                        "environment": "QA",
                        "branch": "${env.SOURCE_BRANCH}",
                        "committed_by": "${env.COMMITTED_BY}",
                        "commit_message": "${env.COMMIT_MSG}",
                        "pr_url": "${env.PR_URL}",
                        "image_tag": "${env.IMAGE_TAG}"
                    }'
                """
            }
        }

        stage('SSH Connection Test') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'qa'
                }
            }
            steps {
                echo 'Testing SSH Connection to Builder VM...'
                sshagent(['buymyverse-ec2-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${BUILDER_USER}@${BUILDER_HOST} '
                            echo "Connected to: "\$(hostname)
                            echo "IP: "\$(hostname -I)
                            echo "Maven: "\$(/usr/bin/mvn -version 2>&1 | head -1)
                            echo "Docker: "\$(/usr/bin/docker --version)
                            echo "AWS: "\$(/usr/bin/aws --version)
                            echo "Java: "\$(java -version 2>&1 | head -1)
                        '
                    """
                }
            }
        }

        stage('Git Pull on Builder') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'qa'
                }
            }
            steps {
                echo 'Pulling latest code on Builder VM...'
                sshagent(['buymyverse-ec2-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${BUILDER_USER}@${BUILDER_HOST} '
                            set -e
                            cd ${PROJECT_DIR}
                            git fetch --all
                            git checkout qa
                            git pull origin qa
                        '
                    """
                }
            }
        }

        stage('Build Maven Project') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'qa'
                }
            }
            steps {
                echo 'Building Maven Project on Builder VM...'
                sshagent(['buymyverse-ec2-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${BUILDER_USER}@${BUILDER_HOST} '
                            set -e
                            cd ${PROJECT_DIR}
                            /usr/bin/mvn clean install -DskipTests
                        '
                    """
                }
            }
        }

        stage('Docker Build') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'qa'
                }
            }
            steps {
                echo 'Building Docker Image on Builder VM...'
                sshagent(['buymyverse-ec2-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${BUILDER_USER}@${BUILDER_HOST} '
                            set -e
                            cd ${PROJECT_DIR}
                            /usr/bin/docker build -t ${ECR_REGISTRY}/${ECR_REPO}:${env.IMAGE_TAG} .
                            /usr/bin/docker tag ${ECR_REGISTRY}/${ECR_REPO}:${env.IMAGE_TAG} ${ECR_REGISTRY}/${ECR_REPO}:latest
                            curl ifconfig.io
                        '
                    """
                }
            }
        }

        stage('Push to ECR') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'qa'
                }
            }
            steps {
                echo 'Pushing Docker Image to ECR from Builder VM...'
                sshagent(['buymyverse-ec2-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${BUILDER_USER}@${BUILDER_HOST} '
                            set -e
                            export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY}
                            export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_KEY}
                            /usr/bin/aws ecr get-login-password --region ${AWS_REGION} | \
                            /usr/bin/docker login --username AWS --password-stdin ${ECR_REGISTRY}
                            /usr/bin/docker push ${ECR_REGISTRY}/${ECR_REPO}:${env.IMAGE_TAG}
                            /usr/bin/docker push ${ECR_REGISTRY}/${ECR_REPO}:latest
                        '
                    """
                }
                script {
                    env.DOCKER_IMAGE = "${ECR_REGISTRY}/${ECR_REPO}:${env.IMAGE_TAG}"
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'qa'
                }
            }
            steps {
                echo 'Updating Kubernetes Deployment with new image...'
                sshagent(['buymyverse-ec2-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${BUILDER_USER}@${BUILDER_HOST} '
                            set -e
                            kubectl set image deployment/${K8S_DEPLOYMENT} \
                                ${K8S_CONTAINER}=${ECR_REGISTRY}/${ECR_REPO}:${env.IMAGE_TAG} \
                                -n ${K8S_NAMESPACE}
                        '
                    """
                }
            }
        }

        stage('Check Pods') {
            when {
                allOf {
                    not { changeRequest() }
                    branch 'qa'
                }
            }
            steps {
                sshagent(['buymyverse-ec2-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${BUILDER_USER}@${BUILDER_HOST} '
                            kubectl get pods -n ${K8S_NAMESPACE}
                        '
                    """
                }
            }
        }
    }

    post {
        success {
            script {
                if (env.BRANCH_NAME == 'qa' && !env.CHANGE_ID) {
                    sh """
                        curl -s -X POST "${TEAMS_URL}" \\
                        -H "Content-Type: application/json" \\
                        -d '{
                            "status": "ended",
                            "job": "${env.JOB_SHORT}",
                            "environment": "QA",
                            "branch": "${env.SOURCE_BRANCH}",
                            "committed_by": "${env.COMMITTED_BY}",
                            "commit_message": "${env.COMMIT_MSG}",
                            "pr_url": "${env.PR_URL}",
                            "image_tag": "${env.IMAGE_TAG}",
                            "docker_image": "${env.DOCKER_IMAGE}",
                            "result": "SUCCESS"
                        }'
                    """
                }
            }
            echo 'Pipeline completed successfully!'
        }

        failure {
            script {
                if (env.BRANCH_NAME == 'qa' && !env.CHANGE_ID) {
                    sh """
                        curl -s -X POST "${TEAMS_URL}" \\
                        -H "Content-Type: application/json" \\
                        -d '{
                            "status": "ended",
                            "job": "${env.JOB_SHORT}",
                            "environment": "QA",
                            "branch": "${env.SOURCE_BRANCH}",
                            "committed_by": "${env.COMMITTED_BY}",
                            "commit_message": "${env.COMMIT_MSG}",
                            "pr_url": "${env.PR_URL}",
                            "image_tag": "${env.IMAGE_TAG}",
                            "result": "FAILED"
                        }'
                    """
                }
            }
            echo 'Pipeline failed!'
        }

        always {
            cleanWs()
        }
    }
}
