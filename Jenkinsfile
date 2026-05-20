pipeline {
    agent any

    stages {
        stage('Connect & Run LS') {
            steps {
                sshagent(['buymyverse-ec2-key']) {
                    sh '''
                        ssh -o StrictHostKeyChecking=no admin@3.226.177.66 "ls"
                    '''
                }
            }
        }
    }
}
