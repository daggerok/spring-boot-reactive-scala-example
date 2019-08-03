# scala + spring-boot + gradle-kotlin-dsl [![Build Status](https://travis-ci.org/daggerok/spring-boot-reactive-scala-example.svg?branch=master)](https://travis-ci.org/daggerok/spring-boot-reactive-scala-example)
Build Reactive apps with Spring Boot Gradle Kotlin DSL starter

## build and run

```bash
./gradlew
java -jar ./build/libs/*.jar
http :8080
```

## Spring MVC

```bash
http :8080/api/mvc # or: http :8080
http :8080/api/mvc body="hello mvc"
http :8080/api/mvc/cd8063ca-4ce7-4b34-81e5-21fe3c066e1d
http :8080/api/mvc/tags
```

## Router Function builder

```bash
http :8080/api/fnb
http :8080/api/fnb body="hello builder"
http :8080/api/fnb/94b6f180-1724-49f9-8b62-8d322f4f8f63
```

## Router Function

```bash
http :8080/api/fn
http :8080/api/fn body="hello function"
http :8080/api/fn/40156273-a6dc-4f6c-a218-1b84d5c30929
```

links:

* see [YouTube: Spring Tips: Bootiful, Reactive Scala](https://www.youtube.com/watch?v=E_YZwrv-zTk)
* update / [migrate Vuepress](https://v1.vuepress.vuejs.org/miscellaneous/migration-guide.html#vuepress-style-styl) 0.x -> 1.x
