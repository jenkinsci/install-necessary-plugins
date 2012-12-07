Uses an addition to the Jenkins remote API in 1.483+ to install all plugins needed to properly load a candidate job.
Can be used as a command-line tool or a Java API.

Usage:

    server=http://your.jenkins/
    java -jar install-necessary-plugins-shaded.jar $server /tmp/config.xml
    curl -d @/tmp/config.xml -H 'Content-Type: application/xml' "${server}doCreateItem?name=newjob"

See: [JENKINS-15003](https://issues.jenkins-ci.org/browse/JENKINS-15003)
