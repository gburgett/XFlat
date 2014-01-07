Steps to upload a release
=====

required apps:
* maven
* gpg - ensure your public key has been uploaded to the public key server

1. Ensure you have a clean workspace, and that the version you want to deploy is on the QA branch.  The POM should specify the version number with "-SNAPSHOT" appended.
2. Ensure you have a local copy of the "release" branch
3. run prepare_release.sh
	* This will create the release commit on the "Release" branch, tag it, test it, and package it.  Afterwards the release commit will be merged back into the "master" branch.  Both branches and all tags will be pushed.
4. run upload_release.sh
	* Verify that you are uploading the correct version when the script prompts
	* The script will prompt multiple times for your gpg key passphrase, enter it each time
5. go to https://oss.sonatype.org/ and close the newly created staging repository
6. download the artifacts from the staging repository and run some simple smoke tests.  If these fail, drop the repository, fix the issues and restart.
7. release the staging repository by clicking the "release" button in the web UI

more info:
https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide