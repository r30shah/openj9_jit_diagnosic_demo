In [1] we have gone through a typical debugging process of narrowing down the problem scope to single failing method and optimization performed in that method that causes your application/test case to fail. This debugging technique can be used and somewhat automated in the case the test/application fails 100% of the time. In the test case we have used in [1], bug resulted in test case throwing Null Pointer Exception. Now a compilation performed by JIT compiler in OpenJ9 VM is affected by many parameters such as application itself, available machine resources, the dynamic profiling data available at the time of compilation, etc. Most of times this nondeterminism makes it difficult to reproduce an error 100 % of the time making the process of finding the issue that JIT compiler causes tedious and time consuming process. For example if a failure occurs 1 time out of 100 runs and in the failing instance a JIT compiler compiles 1024 methods, then performing the debugging steps mentioned in [1] would require a developer to run the test atleast 1000 times (Assuming the best case scenario of everytime picking up the half set of method that contains the failng method). This is with the assumption that environment that causes the original bug to exhibit in test case failure stays same even if JIT compiler is asked to compile limited set of methods and OpenJ9 VM would execute rest of the methods in your application through interpreter which would not be the case most of the times. Changes in run to run environment may make JIT compiler to optimize method differently. In such cases, as we continue to limit down to the failing method, failure rate may also reduces.

To tackle this issue, we developed an in-house debugging agent [2]. This debug agent adds a JNI method [3] which can be used to automate the process of finding a failing method in same environment where test failed originally. To explain how to use the code, let's take a look at the failing test case [4] we have used in [1]. 

I would encourage all to take a look at README document in [5] that summarizes the motivation behind developing debug agent and how to use it nicely. In this document, I would go through some of the modifications we have made in the agent so that it can somehow automate the process.

We realized that typical testing infrastructure such as [JUnit](https://junit.org/junit5/) or [TestNG](https://testng.org/doc/) uses the [Java's method reflection](https://github.com/ibmruntimes/openj9-openjdk-jdk11/blob/901ab9ee51fa31c66123ecff3d7e1aaca2456244/src/java.base/share/classes/java/lang/reflect/Method.java#L552-L567) to invoke a test case method. If we modify that method to call the debug agent's [invoke method](https://github.com/r30shah/openj9-jit-debug-agent/blob/0d630f3901df3c0f12c1211082963a9d7441061e/jcl/src/java.base/share/classes/com/ibm/jit/JITHelpers.java#L1240-L1272), we can take the control of test execution and inspect the exception thrown by the method and trigger the debug agent if the thrown exception is not the expected one. Manually doing this steps still requires us to change the `Method.invoke` from Java Class Libraries and either rebuild the whole JDK or patch java.base module with updated `Method.invoke`. Another challenge we had is how to discard false positives. The testing framework is used to test the JVM behaviour through different test cases which also involves a test cases which are expected to throw exception. Making changes to the standard testing framework and rebuilding them would also a challenge. Not to mention, in automated testing environment, it would be difficult to replace them with the custom one. 

To address the above issue we develoed a [JVM agent](https://github.com/r30shah/openj9-utils/tree/debugagent/jit-debug-agent) which can attach to the JVM and update the bytecodes of both Java Class Library and JUnit test[6] using [ASM framework](https://asm.ow2.io), which allows us to use this agent without rebuilding the JDK or jars for testing framework. 

Let's take a look at the debug agent in action. I have updated the [docker image](https://hub.docker.com/repository/docker/r30shah/jit_debug_agent_demo) with another folder (`/home/jenkins/jit_automated_diagnostic_agent`) that contains this demo. In addition to the tests and jars from this folder, I have also placed a custom JDK (`/home/jenkins/jit_automated_diagnostic_demo_jdk`) build with the debug agent changes in the docker image. JDK also contains the changes that makes the 100% reproducible bug to intermittent issue. So let's first run the test without attaching the debug agent.

```
$ cd /home/jenkins/openj9_jit_diagnosic_demo/jit_automated_diagnostic_agent

$ bash ./build.sh
Removing old compiled classes
Compiling Test_String.java and Test_Runner.java
Note: Test_String.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.


# Running the test multiple times to get the failing rate, as in this case, I
# was able to get the test fail 1 out of 5 times.
$ count=0; while [ $count -lt 5 ]; do
> echo "Running iteration "$count;
> /home/jenkins/jit_automated_diagnostic_demo_jdk/bin/java -cp junit-4.10.jar:. -Xshareclasses:none Test_Runner 20000;
> if [ $? -eq 0 ]; then
>     echo "Test Passed"; 
> else
>     echo "Test Failed";
> fi;
> count=$((count+1));
> done
Running iteration 0
Test Passed
Running iteration 1
Test Passed
Running iteration 2
Test Passed
Running iteration 3
Test Passed
Running iteration 4
test_Constructor13(Test_String): null
Test Failed
```

As we can see that test fails 1 out of 5 times, let's execute the same loop 5 times, this time with attaching the JVM agent.

```
$ count=0; while [ $count -lt 5 ]; do
> echo "Running iteration "$count;
> /home/jenkins/jit_automated_diagnostic_demo_jdk/bin/java -cp junit-4.10.jar:. -Xshareclasses:none -Xjit:forceUsePreexistence -javaagent:jit-debug-agent-1.0.jar Test_Runner 20000;
> if [ $? -eq 0 ]; then
>     echo "Test Passed"; 
> else
>     echo "Test Failed";
> fi;
> count=$((count+1));
> done
Running iteration 0
Test Passed
Running iteration 1
Caught java.lang.NullPointerException inside JITHelpers, thread main
java.lang.NullPointerException
	at java.base/java.lang.String.equals(String.java)
	at Test_String.test_Constructor13(Test_String.java:200)
	at jdk.internal.reflect.GeneratedMethodAccessor13.invoke(Unknown Source)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.base/com.ibm.jit.JITHelpers.invoke(JITHelpers.java:1209)
	at java.base/java.lang.reflect.Method.invoke(Method.java:566)
	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:45)
	at org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:15)
	at org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:42)
	at org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:20)
	at org.junit.runners.ParentRunner.runLeaf(ParentRunner.java:263)
	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:68)
	at org.junit.runners.BlockJUnit4ClassRunner.runChild(BlockJUnit4ClassRunner.java:47)
	at org.junit.runners.ParentRunner$3.run(ParentRunner.java:231)
	at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:60)
	at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:229)
	at org.junit.runners.ParentRunner.access$000(ParentRunner.java:50)
	at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:222)
	at org.junit.runners.ParentRunner.run(ParentRunner.java:300)
	at org.junit.runners.Suite.runChild(Suite.java:128)
	at org.junit.runners.Suite.runChild(Suite.java:24)
	at org.junit.runners.ParentRunner$3.run(ParentRunner.java:231)
	at org.junit.runners.ParentRunner$1.schedule(ParentRunner.java:60)
	at org.junit.runners.ParentRunner.runChildren(ParentRunner.java:229)
	at org.junit.runners.ParentRunner.access$000(ParentRunner.java:50)
	at org.junit.runners.ParentRunner$2.evaluate(ParentRunner.java:222)
	at org.junit.runners.ParentRunner.run(ParentRunner.java:300)
	at org.junit.runner.JUnitCore.run(JUnitCore.java:157)
	at org.junit.runner.JUnitCore.run(JUnitCore.java:136)
	at org.junit.runner.JUnitCore.run(JUnitCore.java:127)
	at org.junit.runner.JUnitCore.runClasses(JUnitCore.java:76)
	at Test_Runner.main(Test_Runner.java:14)
Total number of JIT methods in HashSet = 744
Invalidating PC = 0x7f016c55cf40 java/util/ArrayList.newCapacity(I)I
Rerunning test
Caught exception after invoking test
Invalidating PC = 0x7f016c55dd58 java/lang/Character.highSurrogate(I)C
Rerunning test
Caught exception after invoking test
Invalidating PC = 0x7f016c56c880 java/util/Collections$SynchronizedCollection.<init>(Ljava/util/Collection;)V
Rerunning test
...
Invalidating PC = 0x7f016c586a00 Test_String.test_Constructor13()V
Rerunning test
Identified problematic method
Recompiling PC = 0x7f016c586a00 lastOptIndex = 100 lastOptSubIndex = 1024 Test_String.test_Constructor13()V
Rerunning test
Caught exception after invoking test with lastOptIndex = 100
Recompiling PC = 0x7f016c586f40 lastOptIndex = 99 lastOptSubIndex = 1024 Test_String.test_Constructor13()V
Rerunning test
Caught exception after invoking test with lastOptIndex = 99
...
Recompiling PC = 0x7f016c590100 lastOptIndex = 63 lastOptSubIndex = 1024 Test_String.test_Constructor13()V
Rerunning test
Caught exception after invoking test with lastOptIndex = 63
Recompiling PC = 0x7f016c590660 lastOptIndex = 62 lastOptSubIndex = 1024 Test_String.test_Constructor13()V
Rerunning test
LastOptIndex = 63 is the potential culprit
Recompiling PC = 0x7f016c590bc0 lastOptIndex = 62 lastOptSubIndex = 1024 Test_String.test_Constructor13()V
Rerunning test expecting it to pass
Test passed
Recompiling PC = 0x7f016c591120 lastOptIndex = 63 lastOptSubIndex = 1024 Test_String.test_Constructor13()V
Rerunning test expecting it to fail
Test failed
Aborting JVM
...

# If you take a look at the current directory, the agent also produced a
# compilation log from failing run and passing run for us to compare.
$ ls *.log
badJitCompilationLog_opt_index_63.log goodJitCompilationLog_opt_index_62.log
```

Now as we have gone through the debug agent in action, let me point out some of the limitation of the agent in current state.

1. Currently the JVM agent is made to work with JUnit testing framework as the method that requires modification to avoid false positive when we are using this agent is hardcoded [7]. It requires to be changed if the test fails when using for example testNG or any other standard testing framwork.
2. The code that triggers the debug agent through JNI has the exception that we are seeing in the test case failure hardcoded [8], which means we still have to rebuild the JDK with appropriate changes if the test fails with other kind of exception.
3. When running the test case with JVM agent, it needs additional JIT option `forceUsePreexistence` to allow all methods reverting back to interpreter. We do have changes that can be used to set this option as soon as agent attaches to the JVM [9], but the debug agent in current state does not take advantage of it.

[1]. https://github.com/r30shah/openj9_jit_diagnosic_demo/tree/main/jit_verbose_search#readme
[2]. https://github.com/r30shah/openj9-jit-debug-agent
[3]. https://github.com/r30shah/openj9-jit-debug-agent/blob/0d630f3901df3c0f12c1211082963a9d7441061e/runtime/jcl/common/jithelpers.c#L461-L563
[4]. https://github.com/r30shah/openj9_jit_diagnosic_demo/blob/d9875df644ebaf9227facdbac9e42202507cb4ba/jit_verbose_search/Test_String.java#L196-L201
[5]. https://github.com/r30shah/openj9-jit-debug-agent/blob/debug-agent/README_DEBUGAGENT.md
[6]. https://github.com/r30shah/openj9-utils/blob/72161cbf27ecf62be4153f22ea7f6b8528061f52/jit-debug-agent/src/main/java/DebugAgent.java#L26
[7]. https://github.com/r30shah/openj9-utils/blob/72161cbf27ecf62be4153f22ea7f6b8528061f52/jit-debug-agent/src/main/java/DebugAgent.java#L29
[8]. https://github.com/r30shah/openj9-jit-debug-agent/blob/0d630f3901df3c0f12c1211082963a9d7441061e/jcl/src/java.base/share/classes/com/ibm/jit/JITHelpers.java#L1244
[9]. https://github.com/r30shah/openj9-jit-debug-agent/pull/9