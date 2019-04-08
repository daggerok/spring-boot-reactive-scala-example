package com.github.daggerok

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.{ApplicationRunner, SpringApplication}
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.{Repository, Service}
import org.springframework.web.bind.annotation._
import org.springframework.web.reactive.function.server
import org.springframework.web.reactive.function.server.RequestPredicates.{GET, POST}
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.{RouterFunction, RouterFunctions, ServerRequest, ServerResponse}
import reactor.core.publisher.{Flux, Mono}

import scala.beans.BeanProperty
import scala.collection.JavaConverters
import scala.language.postfixOps

@Document
case class Author(@BeanProperty @Id var name: String)

@Document
case class HashTag(@BeanProperty @Id var name: String)

@Document
case class Tweet(@BeanProperty var body: String,
                 @BeanProperty var author: Author,
                 @BeanProperty @Id var id: UUID = UUID.randomUUID()) {
  @BeanProperty
  var hashTags: Set[HashTag] =
    body.split(" ")
      .collect { case text if text startsWith "#" => HashTag(text.replace("^#\\w+", "") toLowerCase) }
      .toSet
}

@Repository
trait Tweets extends ReactiveMongoRepository[Tweet, UUID]

@Configuration
class AkkaConfig {
  @Bean def actorSystem = ActorSystem.create("TweetsSystem")

  @Bean def actorMaterializer = ActorMaterializer.create(actorSystem)
}

@RestController
class MvcResource(val tweets: Tweets,
                  val actorMaterializer: ActorMaterializer) {

  private val log = LoggerFactory.getLogger(classOf[MvcResource])

  @GetMapping(Array("/api/mvc/{id}"))
  def mvcOne(@PathVariable("id") id: UUID) =
    tweets.findById(id).doFinally(_ => log.info("mvcOne"))

  @GetMapping(Array("/api/mvc/tags"))
  def mvcTags =
    Source
      .fromPublisher(tweets.findAll()) // not akka.stream.scaladsl.JavaFlowSupport.Source
      .map(_.hashTags)
      .reduce((t1, t2) => t1 ++ t2) // flatMapConcat / flatMapMerge?
      .mapConcat(identity)
      //.map(_.name)
      .runWith(Sink.asPublisher(true)) {
        actorMaterializer
      }

  @GetMapping(Array("/**"))
  def mvcGet = tweets.findAll().doFinally(_ => log.info("mvcGet"))

  @PostMapping(Array("/api/mvc/**"))
  def mvcPost(@RequestBody map: java.util.Map[String, String]): Mono[Tweet] = {
    log.info("mvcPost")
    val body = JavaConverters.mapAsScalaMap(map).getOrElse("body", "")
    val tweet = Tweet(body, Author("MVC"))
    tweets.save(tweet)
  }
}

@Service
class TweetsHandlers(val tweets: Tweets) {
  private val log = LoggerFactory.getLogger(classOf[TweetsHandlers])

  def handleOne(request: ServerRequest) = {
    log.info("handleOne")
    val id: String = request.pathVariable("id")
    val tweet = tweets.findById(UUID.fromString(id))
    ServerResponse.ok().body(tweet, classOf[Tweet])
  }

  def handleGet(request: server.ServerRequest) =
    ServerResponse.ok().body(tweets.findAll().doFinally(_ => log.info("handleGet")), classOf[Tweet])

  def handlePost(request: server.ServerRequest): Mono[ServerResponse] = {
    log.info("handlePost")
    val input: Mono[java.util.Map[String, String]] = request.bodyToMono(classOf[java.util.Map[String, String]])
    val body: Mono[String] = input.map(m => m.getOrDefault("body", ""))
    val tweetToBeSaved: Mono[Tweet] = body.map(s => Tweet(s, Author("Fn")))
    val tweet: Mono[Tweet] = tweetToBeSaved.flatMap(t => tweets.save(t))
    ServerResponse.ok().body(tweet, classOf[Tweet])
  }
}

@Configuration
class RouterFunctionBuilderConfig(val handlers: TweetsHandlers) {
  @Bean
  def routerFunctionBuilder =
    RouterFunctions.route()
      .POST("/api/fnb/**", handlers.handlePost _)
      .GET("/api/fnb/{id}", handlers.handleOne _)
      .GET("/api/fnb/**", handlers.handleGet _)
      .build()
}

@Configuration
class RouterFunctionConfig(val handlers: TweetsHandlers) {
  @Bean
  def routerFunction: RouterFunction[ServerResponse] =
    route(POST("/api/fn/**"), handlers.handlePost _)
      .andRoute(GET("/api/fn/{id}"), handlers.handleOne _)
      .andRoute(GET("/api/fn/**"), handlers.handleGet _)
}

@SpringBootApplication
class SpringBootScalaApplication(tweets: Tweets) {
  private val log = LoggerFactory.getLogger(classOf[SpringBootScalaApplication])

  @Bean def init: ApplicationRunner = args => {
    val max = Author("max")
    val dag = Author("dag")
    val daggerok = Author("daggerok")
    val stream = Flux.just(
      Tweet("#spring-boot is nice!", max),
      Tweet("and #scala too!", dag),
      Tweet("But both together are freaking awesome!!111oneoneone #scala with #spring-boot", daggerok)
    )
    tweets
      .deleteAll()
      .thenMany(tweets.saveAll(stream))
      .thenMany(tweets.findAll())
      .subscribe(t => log.info(
        s"""
           |
           |@${t.author.name}:
           |${t.body}
           |hash-tag${if (t.hashTags.size == 1) "" else "s"}: ${t.hashTags.map(_.name).mkString(" ")}
         """.stripMargin)
      )
  }
}

object SpringBootScalaApplication extends App {
  SpringApplication.run(classOf[SpringBootScalaApplication], args: _*)
}
