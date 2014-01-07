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
git reset --hard head
git clean -fdx
git checkout release
git merge --no-ff QA

echo `git status`

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
mv -f pom.xml releases/$version/.

# push the release branch
git push origin release --tags

# re-merge into master
git checkout master
git merge release
git push origin
