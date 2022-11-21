I have built this container image to explain one of the debugging technique, we as a JIT compiler developer uses fairly regularly when we encounter a failure that fails 100% of time. This document will briefly touch JIT compilation verbose log which a developer can collect from the java application or test using JIT command line options and how to use it to find out the failing method from the list of the methods compiled by the JIT compiler when test or application fails. As this document is written with the purpose of teaching simple approach of limiting down the problem to single failing method, I will abstain myself from going into too much details. If you want to find out what other kind of details you can get from JIT verbose log, I would encourage you all to take a look at the OpenJ9 blog about JIT verbose log [1].

A JIT verbose log is a summary of what methods were compiled by the JIT and some of the compilation heuristic decisions that were taken while JIT operates inside the OpenJ9 VM. When a JIT compiler developer encounters a failure that fails 100% of the time, first thing that person would do is to verify if the failure is caused by the incorrectly compiled JIT code. For the demo purpose, I have extracted out the unit test for verifying the functionality of the String class that Java Class Library provides from the OpenJ9 tests [2]. Through the automated tests (a simple summary of how to run them is in [3]) we execute all the test methods from the Test_String class using standard test framework such as JUnit/TestNG. I have uploaded the extracted out test as well as a typical Test_Runner that uses the  JUnit framework and Java's method reflection to invoke the test methods on the github repo [4]. Before we continue on verifying if JIT miscompilation caused the bug or not, let's first go through the test setup.

If you start a container I have created for this demo purpose [5], I have put the setup for studying and using JIT verbose logs to find out method that causes the test to fail in `/home/jenkins/jit_diagnostic_demo/jit_verbose_search`. 
```
$ cd /home/jenkins/jit_diagnostic_demo/jit_verbose_search

$ bash build.sh
Removing old compiled classes
Compiling Test_String.java and Test_Runner.java
Note: Test_String.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.

$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation -cp junit-4.10.jar:. Test_Runner 20
test_Constructor13(Test_String): null
```

As you can see, the test failed, before we go on a debugging process, let's take a look at the command and see what it is trying to do.

1. `/home/jenkins/jit_verbose_demo_jdk/bin/java` : Java executable used to run your application.
2. `-Xshareclasses:none` : OpenJ9 provides facility to share class data between Eclipse OpenJ9 VMs to improve start-up performance and memory footprint (More details in [6]). This option would disable that feature for this demo purpose.
3. `-Xjit:count=10,disableAsyncCompilation` : Command line option to control the behavior of JIT [7][8]. In this option, we are setting the method invocation count to 10. This means once a method has been executed in interpreter 10 times, it will be queued for JIT compilation. Now generally in OpenJ9 VM, the Compilation threads and Application threads runs asynchronously, means while compilation threads are compiling methods from the compilation queue, your application can continue to run. Invocations of the methods which are currently being compiled would be executed by interpreter till JIT compilation of those methods finish and once compiled, all the sub-sequent invocations of those methods will be executing fast compiled code. While executing an application, if a compilation requiest is made to compile certain method, option `disableAsyncCompilation` would make application thread to wait for a compilation result from the compilation thread so that it executes the JIT compiled code resulted from the said compilation.
4. `Test_Runner 20` : Take a look at the `Test_Runner.java` in the same folder, it is the runner program used to execute the tests in `Test_String` class.


So now back to verifying if the test case fails when JIT is turned off or not, we can use the Java command line option `-Xint` to turn off the JIT compilation and execute all the methods in the test/application in interpreted mode.
```
$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xint -cp junit-4.10.jar:. Test_Runner 20
```

Ideally if the failure is only caused by JIT miscompiling a method, the test should pass. In that case, a failure might have occurred because JIT did incorrect compilation and introduced a bug. Now as the test fails 100%, we can use the JIT command line option to collect the verbose log of JIT compilations as following.
```
$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation,verbose,vlog=jitverboseLog -cp junit-4.10.jar:. Test_Runner 20
test_Constructor13(Test_String): null

$ ls jitverboseLog*
jitverboseLog.20221121.215012.1158

$ mv jitverboseLog.20221121.215012.1158 jitVerboseLogForLimitFile // We will be using this file to ask JIT to only compile certain methods, renamed file for simplicity

$ wc -l jitVerboseLogForLimitFile
1305 jitVerboseLogForLimitFile
```

By default in OpenJ9, when it creates any diagnostic files, it suffixes it with information such as date and time when it was produced as well as process id of the JVM that produced it, hence you see that information at the end of the jit verbose log file name as well. In our case, when test failed, JIT compiler in OpenJ9 VM compiled close to 1300 methods(Usually initial 10s or so lines in the jit verbose log provides running environment and jit configurations), and now as we have the list of methods which were compiled when the test failed, we can use the JIT command line option `limitFile` and point it to the collected verbose log to ask JIT to compile only limited set of methods. We can use this approach to find out the single method, miscompilation of which caused the test to fail. As we have total nnn methods and test fails 100%, we can perform a binary search within the list of compiled method, to find out a single failing method by reducing the problem scope to half (In this case, compiled methods) at every re-run of the test and checking out if test passes or not.

```
# Checking if limiting compilation to first 650 methods in the verbose log
# causes the test to fail or not.
$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation,limitFile="(jitVerboseLogForLimitFile,1,650)" -cp junit-4.10.jar:. Test_Runner 20

# Test passes, so all the method printed between line number 1 and 650 does not
# cause the test to fail. So the failing method should be in other half.
# Verifying.
$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation,limitFile="(jitVerboseLogForLimitFile,651,1305)" -cp junit-4.10.jar:. Test_Runner 20
test_Constructor13(Test_String): null
...

# After series of test re-runs, in this case, the failing method was at line
# number 1270, verifying if limiting compilation to that single method still
# causes the test to fail or not.
$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation,limitFile="(jitVerboseLogForLimitFile,1270,1270)" -cp junit-4.10.jar:. Test_Runner 20
test_Constructor13(Test_String): null
```

Now after re-running the test atmost 2*log(n) (includes sanity check of other half as well) times, you should be able to limit the problem scope to a single method in the test, doing a JIT compilation of which cause the test to fail. That would be our failing method. In the above case, after doing a binary search, method at line number 1270 in the `jitVerboseLogForLimitFile` is our failing method. A JIT compiler developer next can collect the compilation log using following command to collect the compilation log of that method and inspect the log file to find out why it failed. Just for knowledge, pasting a markdown document in [8] which provides a guide in to how to read the log file.

```
# Finding out the name of the failing method
$ head -n 1270 jitVerboseLogForLimitFile | tail -n1
+ (warm) Test_String.test_Constructor13()V @ 00007F3B29A20A40-00007F3B29A20C88 OrdinaryMethod - Q_SZ=0 Q_SZI=0 QW=6 j9m=00000000001B1E00 bcsz=27 sync compThreadID=0 CpuLoad=0%(0%avg) JvmCpu=44%

# Re-running the test, now with collecting the compilation log of the
# Test_String.test_Constructor13()V
$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation,limitFile="(jitVerboseLogForLimitFile,1270,1270)","{Test_String.test_Constructor13()V}(traceFull,log=failing.methodLog) -cp junit-4.10.jar:. Test_Runner 20
test_Constructor13(Test_String): null
```

Now, in the compilation log file in this case, there are total `nnn` optimizations which were performed (Looking at the last `<optimization id=` string in the log file. We can use similar approach as we did in finding out the single method that causes the test to fail to find out the single optimization within that method that causes the method to fail by using `lastOptIndex` JIT command line option on that method as following.

```
# Finding out the last optimization index that was performed in the method.
$ grep -rn "optimization id=" failingMethodLog.1213.68468.20221121.220748.1213 | tail -n1
12390:<optimization id=99 name=hotFieldMarking method=Test_String.test_Constructor13()V>

# Running with lastOptIndex=99 for sanity (Test should fail)
$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation,limitFile="(jitVerboseLogForLimitFile,1270,1270)","{Test_String.test_Constructor13()V}(lastOptIndex=99) -cp junit-4.10.jar:. Test_Runner 20
test_Constructor13(Test_String): null

# Now narrowing down the lastOptIndex search, with last performed optimization
# at index 50, does the test passes or fails? 
$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation,limitFile="(jitVerboseLogForLimitFile,1270,1270)","{Test_String.test_Constructor13()V}(lastOptIndex=50) -cp junit-4.10.jar:. Test_Runner 20
# Test passes, checking it further
...


$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation,limitFile="(jitVerboseLogForLimitFile,1270,1270)","{Test_String.test_Constructor13()V}(lastOptIndex=60) -cp junit-4.10.jar:. Test_Runner 20
test_Constructor13(Test_String): null

# Test fails at optimization id 60, checking if it fails at optimization id 59
# or not.
$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation,limitFile="(jitVerboseLogForLimitFile,1270,1270)","{Test_String.test_Constructor13()V}(lastOptIndex=59) -cp junit-4.10.jar:. Test_Runner 20
# Test Passes, so definitely optimization performed at index 60 in
# Test_String.test_Constructor13()V is the cause of failure. Now re-running test
# to get the compilation log of passing and failing run to compare

# Collecting compilation log from failing run
$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation,limitFile="(jitVerboseLogForLimitFile,1270,1270)","{Test_String.test_Constructor13()V}(traceFull,log=bad.methodLog,lastOptIndex=60) -cp junit-4.10.jar:. Test_Runner 20
test_Constructor13(Test_String): null

# Collecting compilation log from passing run
$ /home/jenkins/jit_verbose_demo_jdk/bin/java -Xshareclasses:none -Xnoaot -Xjit:count=10,disableAsyncCompilation,limitFile="(jitVerboseLogForLimitFile,1270,1270)","{Test_String.test_Constructor13()V}(traceFull,log=good.methodLog,lastOptIndex=59) -cp junit-4.10.jar:. Test_Runner 20
```

Once we find out the `lastOptIndex` N that causes the test to fail but N-1 makes the test pass, we have further reduced down the problem scope by identifying the optimization within the method compilation which causes the failure, and now we can last time collect the compilation log file with lastOptIndex `N` terming it badLog, and `N-1` terming it goodLog and compare them to find out the cause of the failure. In this case, we have created a buggy JDK that in which, optimizer replaces the newly created valid string object with NULL pointer which causes the test to fail with Null Pointer Exception, which you can see in the badLog (Look for following output in the badLog file).


[1]. https://blog.openj9.org/2018/06/07/reading-verbose-jit-logs/
[2]. https://github.com/eclipse-openj9/openj9/blob/master/test/functional/Java8andUp/src/org/openj9/test/java/lang/Test_String.java
[3]. https://github.com/eclipse-openj9/openj9/tree/master/test#readme
[4]. https://github.com/r30shah/openj9_jit_diagnosic_demo/tree/main/jit_verbose_search 
[5]. https://hub.docker.com/repository/docker/r30shah/jit_diagnostic_demo
[6]. https://www.eclipse.org/openj9/docs/shrc/
[7]. https://www.eclipse.org/openj9/docs/xjit/
[8]. https://github.com/eclipse/omr/blob/master/doc/compiler/optimizer/IntroReadLogFile.md

