
set -e
set -u

git checkout release
current=`mvn help:evaluate -Dexpression=project.version 2>/dev/null| grep -v "^\["`

if [[ "$current" == *-SNAPSHOT ]]
then
	echo "cannot upload a snapshot release ($current)"
	exit -1
fi

if [[ ! -e releases/$current/xflat-$current.jar || 
		! -e releases/$current/xflat-$current-sources.jar ||
		! -e releases/$current/xflat-$current-javadoc.jar ||
		! -e releases/$current/pom.xml		]]
then
	echo "artifacts for version $current not found!"
	exit -1
fi

read -e -p "Deploy version $current to Maven Central? (y/n)" deploy
if [[ $deploy == "y" ]] 
then
	mvn gpg:sign-and-deploy-file -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -DpomFile=releases/$current/pom.xml -Dfile=releases/$current/xflat-$current.jar
	mvn gpg:sign-and-deploy-file -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -DpomFile=releases/$current/pom.xml -Dfile=releases/$current/xflat-$current-sources.jar -Dclassifier=sources
	mvn gpg:sign-and-deploy-file -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -DpomFile=releases/$current/pom.xml -Dfile=releases/$current/xflat-$current-javadoc.jar -Dclassifier=javadoc
	echo "deployed version $current to sonatype repo"
fi

exit 0