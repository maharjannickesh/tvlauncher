# TV Launcher

Compose-for-TV launcher for a Chromecast with Google TV (10.0.0.21, wireless adb).

System Java is 27; Gradle needs 21:

    export JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/jdk-21.0.12.1+1/Contents/Home

- Build: `./gradlew assembleDebug`
- Install and launch on the TV: `tools/tv.sh connect && tools/tv.sh install`
- Screenshot to `shots/<name>.png`: `tools/tv.sh shot <name>`
