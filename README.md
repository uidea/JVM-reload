# JVM-reload
利用Instrumentation Premain(1.5)技术实现的Java代码热加载技术

热加载项目在core目录下, example目录下是示例代码.

如果要实现代码的热加载很简单, 只需要将core里面的项目打包成一个jar包然后在启动App时代理该jar包就可以了, 例如
```
java -javaagent:./reload-agent-1.0-SNAPSHOT.jar -cp .;./* TestReload
```
同时要在classpath下放一个`reload.properties`，程序会根据里面的配置来监控某个目录下的某个文件, 如果文件发生变动了, 就重新加载该文件.例如
```
watchPath=./
fileName=moniter.jar
prfixName=wang.ming15
```
例如上面的配置只会监控`./`目录下的`moniter.jar`jar包, 且只会热加载`wang.ming15`开头的Java文件.
