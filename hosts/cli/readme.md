# cli

## cd

cd to your cli project path eg.`C:\Users\Administrator\IdeaProjects\a2a4j-examples\hosts\cli`

## compile to jar with maven

```cmd
mvn clean package
```

## run jar

```cmd
java -jar .\target\cli-2.0.1.jar  -u http://127.0.0.1:8901 -s
```

answer:

```text
please input your question! input :exit will exit.
hello jack
[893de6e443a842aa8de7870384e3d008:2d8547c6caa546719060fadfbd304f07] I'm echo agent! echo: hello jack
ctrl+c exit
```