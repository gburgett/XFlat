#!bin/sh
set -e
set -u


current=`mvn help:evaluate -Dexpression=project.version 2>/dev/null| grep -v "^\["`

if [[ "$current" == *-SNAPSHOT ]]
then
	echo "cannot upload a snapshot release ($current)"
	exit -1
fi

if [[ ! -e releases/$current/xflat-$current.jar || 
		! -e releases/$current/xflat-$current-javadoc.jar ||
		! -e releases/$current/xflat-$current-javadoc.jar ]]
then
	echo "artifacts for version $current not found!"
	exit -1
fi

read -e -p "Deploy version $current to Maven Central? (y/n)" deploy
if [[ $deploy == "y" ]] 
then
	echo "deploying..."
	# gpg:sign-and-deploy-file -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -Dfile=ossrh-test-1.2.jar
	# gpg:sign-and-deploy-file -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -Dfile=ossrh-test-1.2-sources.jar -Dclassifier=sources
	# gpg:sign-and-deploy-file -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -Dfile=ossrh-test-1.2-javadoc.jar -Dclassifier=javadoc
	echo "deployed version $current to sonatype repo"
fi

exit 0