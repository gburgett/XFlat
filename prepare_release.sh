#!bin/sh
set -e
set -u

# identify the version
current=`mvn help:evaluate -Dexpression=project.version 2>/dev/null| grep -v "^\["`

case $current in
	*-SNAPSHOT) 
		version=`echo $current | cut -d '-' -f 1`
		echo "packaging XFlat release version $version from $current"
		;;
	*) echo "project must be a snapshot version";;
esac

# merge from QA into release
git clean -fdx
git checkout release
git merge --no-ff QA

# update the version number and tag the release version
mvn versions:set -DnewVersion=$version -DgenerateBackupPoms=false
git add pom.xml
git commit -m "[release] Prepared release version $version"
git tag -a v$version -m "[release] Sources for release version $version"

# package the files
mvn package

# move the packaged files into the release folder
mkdir -p releases/$version
mv -f target/xflat-*.jar releases/$version/.

# push the release branch
git push origin release

# re-merge into master
git checkout master
git merge release
git push master

# read -e -p "Deploy the artifacts to Maven Central? (y/n)" -i "n" deploy
# if [[ $deploy == "y" ]] then
	# gpg:sign-and-deploy-file -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -DpomFile=ossrh-test-1.2.pom -Dfile=ossrh-test-1.2.jar
	# gpg:sign-and-deploy-file -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -DpomFile=ossrh-test-1.2.pom -Dfile=ossrh-test-1.2-sources.jar -Dclassifier=sources
	# gpg:sign-and-deploy-file -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -DpomFile=ossrh-test-1.2.pom -Dfile=ossrh-test-1.2-javadoc.jar -Dclassifier=javadoc
# else
	# echo 
# fi