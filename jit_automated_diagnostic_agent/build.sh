#!/usr/bin/env bash

echo "Removing old compiled classes"
rm *.class

echo "Compiling Test_String.java and Test_Runner.java"
/home/jenkins/jit_verbose_demo_jdk/bin/javac -cp junit-4.10.jar:. Test_String.java Test_Runner.java
