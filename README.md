# JVM-reload
利用Instrumentation Premain(1.5)技术实现的Java代码热加载技术

热加载项目在core目录下, example目录下是示例代码.

如果要实现代码的热加载很简单, 只需要将core里面的项目打包成一个jar包然后在启动App时代理该jar包就可以了, 例如
```
java -javaagent:./reload-agent-1.0-SNAPSHOT.jar -cp .;./* TestReload
```
