pipeline {
    agent any

    stages {
        stage('SSH & List Files') {
            steps {
                sshagent(['buymyverse-ec2-key']) {
                    sh '''
                        ssh -o StrictHostKeyChecking=no admin@3.226.177.66 "
                            echo 'Connected to: '$(hostname)
                            echo 'IP: '$(hostname -I)
                            echo '--- Home Directory ---'
                            ls -la ~
                        "
                    '''
                }
            }
        }
    }
}
